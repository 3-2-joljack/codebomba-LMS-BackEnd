package com.wanted.codebombalms.global.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class S3ObjectStorageTest {

    private static final String RUN_TEST_ENV = "RUN_S3_CONNECTION_TEST";
    private static final String BUCKET_ENV = "S3_CONNECTION_TEST_BUCKET";

    @Test
    @DisplayName("공개 URL 은 버킷 가상 호스트 주소이고 키의 특수문자를 인코딩한다")
    void publicUrl_encodesKey() {
        S3ObjectStorage storage = storage("codebomba-lms");

        assertThat(storage.publicUrl("course_thumbnail_images/a b+c.png"))
                .isEqualTo("https://codebomba-lms.s3.ap-northeast-2.amazonaws.com/course_thumbnail_images/a%20b%2Bc.png");
    }

    @Test
    @DisplayName("공개 URL 에서 키를 되돌린다")
    void keyFromPublicUrl_roundTrips() {
        S3ObjectStorage storage = storage("codebomba-lms");
        String key = "course_thumbnail_images/a b+c.png";

        assertThat(storage.keyFromPublicUrl(storage.publicUrl(key))).isEqualTo(key);
    }

    @Test
    @DisplayName("다른 호스트, 다른 버킷, 잘못된 URL 은 null")
    void keyFromPublicUrl_rejectsForeignUrls() {
        S3ObjectStorage storage = storage("codebomba-lms");

        assertThat(storage.keyFromPublicUrl("https://example.com/course_thumbnail_images/a.png")).isNull();
        assertThat(storage.keyFromPublicUrl("https://other.s3.ap-northeast-2.amazonaws.com/a.png")).isNull();
        assertThat(storage.keyFromPublicUrl("https://storage.googleapis.com/codebomba-lms/a.png")).isNull();
        assertThat(storage.keyFromPublicUrl("://not-a-url")).isNull();
        assertThat(storage.keyFromPublicUrl(" ")).isNull();
        assertThat(storage.keyFromPublicUrl(null)).isNull();
    }

    @Test
    @DisplayName("기본 자격 증명으로 S3 버킷에 올리고, 서명 URL 로 읽고, 삭제할 수 있다")
    @EnabledIfEnvironmentVariable(named = RUN_TEST_ENV, matches = "true")
    void putPresignDelete_againstRealBucket() throws Exception {
        S3ObjectStorage storage = storage(System.getenv(BUCKET_ENV));
        String key = "s3-connection-test/" + UUID.randomUUID() + ".txt";
        String body = "S3 connection test 한글";

        try {
            storage.put(key, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));

            String url = storage.presignedDownloadUrl(
                    key,
                    Duration.ofMinutes(1),
                    "attachment; filename=\"test.txt\""
            );
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );

            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).isEqualTo(body);
            assertThat(response.headers().firstValue("content-disposition"))
                    .hasValue("attachment; filename=\"test.txt\"");
        } finally {
            storage.delete(key);
        }
    }

    private S3ObjectStorage storage(String bucket) {
        ObjectStorageProperties properties = new ObjectStorageProperties();
        properties.setBucket(bucket);
        return new S3ObjectStorage(properties);
    }
}
