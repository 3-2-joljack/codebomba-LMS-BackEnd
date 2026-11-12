package com.wanted.codebombalms.course.infrastructure.storage;

import com.wanted.codebombalms.course.application.port.CourseThumbnailStoragePort;
import com.wanted.codebombalms.course.domain.exception.CourseErrorCode;
import com.wanted.codebombalms.global.domain.common.error.exception.ExternalServiceException;
import com.wanted.codebombalms.global.domain.common.error.exception.ValidationException;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorage;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorageProperties;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class S3CourseThumbnailStorageAdapter implements CourseThumbnailStoragePort {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final ObjectStorageProperties properties;
    private final ObjectStorage objectStorage;

    public S3CourseThumbnailStorageAdapter(
            ObjectStorageProperties properties,
            ObjectStorage objectStorage
    ) {
        this.properties = properties;
        this.objectStorage = objectStorage;
    }

    @Override
    public StoredCourseThumbnail upload(
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
                properties.getCourseThumbnailPrefix(),
                UUID.randomUUID() + "-" + sanitizedFileName
        );

        try {
            objectStorage.put(objectName, contentType, imageBytes);

            return new StoredCourseThumbnail(
                    sanitizedFileName,
                    objectName,
                    contentType,
                    fileSize,
                    objectStorage.publicUrl(objectName)
            );
        } catch (Exception e) {
            throw new ExternalServiceException(
                    CourseErrorCode.COURSE_THUMBNAIL_UPLOAD_FAILED,
                    e
            );
        }
    }

    @Override
    public void delete(String thumbnailUrl) {
        String objectName = extractObjectName(thumbnailUrl);
        if (objectName == null || objectName.isBlank()) {
            return;
        }

        try {
            objectStorage.delete(objectName);
        } catch (Exception e) {
            throw new ExternalServiceException(
                    CourseErrorCode.COURSE_THUMBNAIL_DELETE_FAILED,
                    e
            );
        }
    }

    /** 이 버킷의 썸네일 prefix 아래 객체일 때만 키를 돌려준다. */
    private String extractObjectName(String thumbnailUrl) {
        String objectName = objectStorage.keyFromPublicUrl(thumbnailUrl);
        if (objectName == null) {
            return null;
        }

        String thumbnailPrefix = ObjectStorageProperties.normalizePrefix(
                properties.getCourseThumbnailPrefix()
        );
        if (!thumbnailPrefix.isBlank() && !objectName.startsWith(thumbnailPrefix + "/")) {
            return null;
        }

        return objectName;
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
                || imageBytes.length == 0
                || fileSize != imageBytes.length
                || !hasValidImageSignature(contentType, imageBytes);

        if (invalidFile) {
            throw new ValidationException(
                    CourseErrorCode.COURSE_THUMBNAIL_INVALID_FILE
            );
        }
    }

    private boolean hasValidImageSignature(
            String contentType,
            byte[] imageBytes
    ) {
        return switch (contentType) {
            case "image/jpeg" -> isJpeg(imageBytes);
            case "image/png" -> isPng(imageBytes);
            case "image/webp" -> isWebp(imageBytes);
            default -> false;
        };
    }

    private boolean isJpeg(byte[] imageBytes) {
        return imageBytes.length >= 3
                && (imageBytes[0] & 0xFF) == 0xFF
                && (imageBytes[1] & 0xFF) == 0xD8
                && (imageBytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] imageBytes) {
        byte[] pngSignature = new byte[] {
                (byte) 0x89,
                0x50,
                0x4E,
                0x47,
                0x0D,
                0x0A,
                0x1A,
                0x0A
        };

        return startsWith(imageBytes, pngSignature);
    }

    private boolean isWebp(byte[] imageBytes) {
        return imageBytes.length >= 12
                && imageBytes[0] == 'R'
                && imageBytes[1] == 'I'
                && imageBytes[2] == 'F'
                && imageBytes[3] == 'F'
                && imageBytes[8] == 'W'
                && imageBytes[9] == 'E'
                && imageBytes[10] == 'B'
                && imageBytes[11] == 'P';
    }

    private boolean startsWith(
            byte[] imageBytes,
            byte[] signature
    ) {
        if (imageBytes.length < signature.length) {
            return false;
        }

        for (int index = 0; index < signature.length; index++) {
            if (imageBytes[index] != signature[index]) {
                return false;
            }
        }

        return true;
    }

    private String sanitizeFileName(String originalFileName) {
        return originalFileName
                .replace("\\", "_")
                .replace("/", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
