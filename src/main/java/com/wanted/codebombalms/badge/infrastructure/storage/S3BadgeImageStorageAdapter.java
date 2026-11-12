package com.wanted.codebombalms.badge.infrastructure.storage;

import com.wanted.codebombalms.badge.application.port.BadgeImageStoragePort;
import com.wanted.codebombalms.badge.exception.BadgeErrorCode;
import com.wanted.codebombalms.global.domain.common.error.exception.ExternalServiceException;
import com.wanted.codebombalms.global.domain.common.error.exception.ValidationException;
import com.wanted.codebombalms.global.infrastructure.cache.CacheNames;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorage;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class S3BadgeImageStorageAdapter implements BadgeImageStoragePort {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Duration ACCESS_URL_DURATION = Duration.ofMinutes(10);

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final ObjectStorageProperties properties;
    private final ObjectStorage objectStorage;

    public S3BadgeImageStorageAdapter(
            ObjectStorageProperties properties,
            ObjectStorage objectStorage
    ) {
        this.properties = properties;
        this.objectStorage = objectStorage;
    }

    @Override
    public StoredBadgeImage upload(
            String originalFileName,
            String contentType,
            long fileSize,
            byte[] imageBytes
    ) {
        validateImage(
                originalFileName,
                contentType,
                fileSize,
                imageBytes
        );

        String sanitizedFileName = sanitizeFileName(originalFileName);
        String objectName = ObjectStorageProperties.objectKey(
                properties.getBadgeImagePrefix(),
                UUID.randomUUID() + "-" + sanitizedFileName
        );

        try {
            objectStorage.put(objectName, contentType, imageBytes);

            return new StoredBadgeImage(
                    sanitizedFileName,
                    objectName,
                    contentType,
                    fileSize
            );
        } catch (Exception e) {
            log.error(
                    "S3 badge image upload failed. bucket={}, objectName={}, contentType={}, fileSize={}",
                    properties.getBucket(),
                    objectName,
                    contentType,
                    fileSize,
                    e
            );

            throw new ExternalServiceException(
                    BadgeErrorCode.BADGE_IMAGE_UPLOAD_FAILED,
                    e
            );
        }
    }

    @Cacheable(
            cacheNames = CacheNames.BADGE_IMAGE_ACCESS_URL,
            key = "T(com.wanted.codebombalms.badge.infrastructure.cache.BadgeCacheKeys).badgeImageAccessUrl(#objectName)",
            condition = "#objectName != null && !#objectName.isBlank()",
            unless = "#result == null"
    )
    @Override
    public String generateAccessUrl(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            throw new ValidationException(
                    BadgeErrorCode.BADGE_IMAGE_INVALID_FILE
            );
        }

        try {
            return objectStorage.presignedGetUrl(objectName, ACCESS_URL_DURATION);
        } catch (Exception e) {
            throw new ExternalServiceException(
                    BadgeErrorCode.BADGE_IMAGE_ACCESS_URL_FAILED,
                    e
            );
        }
    }

    @CacheEvict(
            cacheNames = CacheNames.BADGE_IMAGE_ACCESS_URL,
            key = "T(com.wanted.codebombalms.badge.infrastructure.cache.BadgeCacheKeys).badgeImageAccessUrl(#objectName)",
            condition = "#objectName != null && !#objectName.isBlank()",
            beforeInvocation = true
    )
    @Override
    public void delete(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }

        try {
            objectStorage.delete(objectName);
        } catch (Exception e) {
            throw new ExternalServiceException(
                    BadgeErrorCode.BADGE_IMAGE_DELETE_FAILED,
                    e
            );
        }
    }

    private void validateImage(
            String originalFileName,
            String contentType,
            long fileSize,
            byte[] imageBytes
    ) {
        boolean invalidFile = originalFileName == null
                || originalFileName.isBlank()
                || contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType)
                || fileSize <= 0
                || fileSize > MAX_FILE_SIZE
                || imageBytes == null
                || imageBytes.length == 0;

        if (invalidFile) {
            throw new ValidationException(
                    BadgeErrorCode.BADGE_IMAGE_INVALID_FILE
            );
        }
    }

    private String sanitizeFileName(String originalFileName) {
        return originalFileName
                .replace("\\", "_")
                .replace("/", "_");
    }
}
