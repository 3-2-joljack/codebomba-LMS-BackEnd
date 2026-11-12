package com.wanted.codebombalms.global.infrastructure.storage;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS S3 구현. 자격 증명은 SDK 기본 체인(EC2 인스턴스 역할, 환경변수, ~/.aws)을 따른다.
 * 클라이언트는 처음 쓸 때 만든다 — 자격 증명이 없는 환경에서도 서버 기동은 되도록.
 */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private final ObjectStorageProperties properties;
    private volatile S3Client client;
    private volatile S3Presigner presigner;

    public S3ObjectStorage(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public void put(String key, String contentType, byte[] content) {
        client().putObject(
                request -> request.bucket(bucket()).key(key).contentType(contentType),
                RequestBody.fromBytes(content)
        );
    }

    @Override
    public void put(String key, String contentType, InputStream content, long contentLength) {
        client().putObject(
                request -> request.bucket(bucket()).key(key).contentType(contentType),
                RequestBody.fromInputStream(content, contentLength)
        );
    }

    @Override
    public void delete(String key) {
        client().deleteObject(request -> request.bucket(bucket()).key(key));
    }

    @Override
    public String presignedGetUrl(String key, Duration duration) {
        return presign(GetObjectRequest.builder().bucket(bucket()).key(key).build(), duration);
    }

    @Override
    public String presignedDownloadUrl(String key, Duration duration, String contentDisposition) {
        return presign(
                GetObjectRequest.builder()
                        .bucket(bucket())
                        .key(key)
                        .responseContentDisposition(contentDisposition)
                        .build(),
                duration
        );
    }

    @Override
    public String publicUrl(String key) {
        String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8)
                .replace("+", "%20")
                .replace("%2F", "/");
        return "https://" + publicHost() + "/" + encodedKey;
    }

    @Override
    public String keyFromPublicUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            if (!publicHost().equals(uri.getHost()) || path == null || path.length() < 2) {
                return null;
            }
            // URI.getPath() 가 이미 퍼센트 디코딩을 한다. 한 번 더 디코딩하면 '+' 가 공백으로 바뀐다.
            return path.substring(1);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String presign(GetObjectRequest request, Duration duration) {
        return presigner()
                .presignGetObject(builder -> builder
                        .signatureDuration(duration)
                        .getObjectRequest(request))
                .url()
                .toString();
    }

    private String publicHost() {
        return bucket() + ".s3." + properties.getRegion() + ".amazonaws.com";
    }

    private String bucket() {
        return properties.getBucket();
    }

    private S3Client client() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    client = S3Client.builder()
                            .region(Region.of(properties.getRegion()))
                            .build();
                }
            }
        }
        return client;
    }

    private S3Presigner presigner() {
        if (presigner == null) {
            synchronized (this) {
                if (presigner == null) {
                    presigner = S3Presigner.builder()
                            .region(Region.of(properties.getRegion()))
                            .build();
                }
            }
        }
        return presigner;
    }
}
