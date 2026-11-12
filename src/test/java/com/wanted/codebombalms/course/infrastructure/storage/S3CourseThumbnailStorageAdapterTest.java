package com.wanted.codebombalms.course.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.codebombalms.course.application.port.CourseThumbnailStoragePort.StoredCourseThumbnail;
import com.wanted.codebombalms.course.domain.exception.CourseErrorCode;
import com.wanted.codebombalms.global.domain.common.error.exception.ExternalServiceException;
import com.wanted.codebombalms.global.domain.common.error.exception.ValidationException;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorage;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorageProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("S3CourseThumbnailStorageAdapter test")
class S3CourseThumbnailStorageAdapterTest {

    private static final String THUMBNAIL_URL =
            "https://codebomba-lms.s3.ap-northeast-2.amazonaws.com/course_thumbnail_images/java.png";
    private static final byte[] PNG_BYTES = new byte[] {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private final ObjectStorage objectStorage = mock(ObjectStorage.class);
    private final S3CourseThumbnailStorageAdapter adapter =
            new S3CourseThumbnailStorageAdapter(new ObjectStorageProperties(), objectStorage);

    @Test
    void upload_storesUnderThumbnailPrefixAndReturnsPublicUrl() {
        when(objectStorage.publicUrl(anyString())).thenReturn(THUMBNAIL_URL);

        StoredCourseThumbnail stored = adapter.upload(
                "java 썸네일.png",
                "image/png",
                PNG_BYTES.length,
                PNG_BYTES
        );

        verify(objectStorage).put(eq(stored.objectName()), eq("image/png"), eq(PNG_BYTES));
        assertThat(stored.objectName()).startsWith("course_thumbnail_images/");
        assertThat(stored.objectName()).endsWith("-java____.png");
        assertThat(stored.accessUrl()).isEqualTo(THUMBNAIL_URL);
    }

    @Test
    void upload_throwsValidation_whenContentTypeIsImageButBytesAreNotImage() {
        byte[] fakeImageBytes = "not image".getBytes();

        assertThatThrownBy(() -> adapter.upload(
                "thumbnail.png",
                "image/png",
                fakeImageBytes.length,
                fakeImageBytes
        ))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(CourseErrorCode.COURSE_THUMBNAIL_INVALID_FILE);
    }

    @Test
    void upload_throwsValidation_whenFileSizeDoesNotMatchByteLength() {
        assertThatThrownBy(() -> adapter.upload(
                "thumbnail.png",
                "image/png",
                PNG_BYTES.length + 1,
                PNG_BYTES
        ))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(CourseErrorCode.COURSE_THUMBNAIL_INVALID_FILE);
    }

    @Test
    void upload_throwsExternalServiceException_whenStoragePutFails() {
        doThrow(new RuntimeException("put failed"))
                .when(objectStorage).put(anyString(), anyString(), any(byte[].class));

        assertThatThrownBy(() -> adapter.upload(
                "thumbnail.png",
                "image/png",
                PNG_BYTES.length,
                PNG_BYTES
        ))
                .isInstanceOf(ExternalServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CourseErrorCode.COURSE_THUMBNAIL_UPLOAD_FAILED);
    }

    @Test
    void delete_deletesObject_whenUrlBelongsToConfiguredBucket() {
        when(objectStorage.keyFromPublicUrl(THUMBNAIL_URL))
                .thenReturn("course_thumbnail_images/java.png");

        adapter.delete(THUMBNAIL_URL);

        verify(objectStorage).delete("course_thumbnail_images/java.png");
    }

    @Test
    void delete_ignoresUrl_whenUrlDoesNotBelongToConfiguredBucket() {
        when(objectStorage.keyFromPublicUrl(anyString())).thenReturn(null);

        adapter.delete("https://example.com/images/java.png");

        verify(objectStorage, never()).delete(anyString());
    }

    @Test
    void delete_ignoresUrl_whenObjectIsOutsideCourseThumbnailPrefix() {
        when(objectStorage.keyFromPublicUrl(anyString()))
                .thenReturn("lecture_materials/guide.pdf");

        adapter.delete("https://codebomba-lms.s3.ap-northeast-2.amazonaws.com/lecture_materials/guide.pdf");

        verify(objectStorage, never()).delete(anyString());
    }

    @Test
    void delete_ignoresNullUrl() {
        assertThatCode(() -> adapter.delete(null)).doesNotThrowAnyException();

        verify(objectStorage, never()).delete(anyString());
    }

    @Test
    void delete_throwsExternalServiceException_whenStorageDeleteFails() {
        when(objectStorage.keyFromPublicUrl(THUMBNAIL_URL))
                .thenReturn("course_thumbnail_images/java.png");
        doThrow(new RuntimeException("delete failed"))
                .when(objectStorage).delete("course_thumbnail_images/java.png");

        assertThatThrownBy(() -> adapter.delete(THUMBNAIL_URL))
                .isInstanceOf(ExternalServiceException.class)
                .extracting("errorCode")
                .isEqualTo(CourseErrorCode.COURSE_THUMBNAIL_DELETE_FAILED);
    }
}
