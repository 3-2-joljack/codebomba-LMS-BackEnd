package com.wanted.codebombalms.lecture.infrastructure.storage;

import com.wanted.codebombalms.global.domain.common.error.exception.ExternalServiceException;
import com.wanted.codebombalms.global.domain.common.error.exception.ValidationException;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorage;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorageProperties;
import com.wanted.codebombalms.lecture.application.port.LectureMaterialStoragePort;
import com.wanted.codebombalms.lecture.domain.exception.LectureErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3LectureMaterialStorageAdapter implements LectureMaterialStoragePort {

    private static final long MAX_FILE_SIZE = 20 * 1024 * 1024;
    private static final Duration DOWNLOAD_URL_DURATION = Duration.ofMinutes(1);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain",
            "text/csv",
            "application/zip"
    );

    private final ObjectStorageProperties properties;
    private final ObjectStorage objectStorage;

    @Override
    public StoredLectureMaterial upload(
            String originalFileName,
            String contentType,
            long fileSize,
            byte[] content
    ) {
        validateFile(originalFileName, contentType, fileSize, content);

        String sanitizedFileName = sanitizeFileName(originalFileName);
        String storedFileName = UUID.randomUUID() + "_" + sanitizedFileName;
        String objectName = ObjectStorageProperties.objectKey(
                properties.getLectureMaterialPrefix(),
                storedFileName
        );

        try {
            objectStorage.put(objectName, contentType, content);
            return new StoredLectureMaterial(
                    originalFileName,
                    storedFileName,
                    objectName,
                    contentType,
                    fileSize
            );
        } catch (Exception e) {
            throw new ExternalServiceException(
                    LectureErrorCode.LECTURE_MATERIAL_UPLOAD_FAILED,
                    e
            );
        }
    }

    @Override
    public void delete(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        try {
            objectStorage.delete(filePath);
        } catch (Exception e) {
            throw new ExternalServiceException(
                    LectureErrorCode.LECTURE_MATERIAL_DELETE_FAILED,
                    e
            );
        }
    }

    @Override
    public String generateDownloadUrl(String filePath, String originalFileName) {
        if (filePath == null || filePath.isBlank()
                || originalFileName == null || originalFileName.isBlank()) {
            throw new ValidationException(LectureErrorCode.LECTURE_MATERIAL_INVALID_FILE);
        }

        String contentDisposition = ContentDisposition.attachment()
                .filename(originalFileName, StandardCharsets.UTF_8)
                .build()
                .toString();

        try {
            return objectStorage.presignedDownloadUrl(
                    filePath,
                    DOWNLOAD_URL_DURATION,
                    contentDisposition
            );
        } catch (Exception e) {
            throw new ExternalServiceException(
                    LectureErrorCode.LECTURE_MATERIAL_DOWNLOAD_URL_FAILED,
                    e
            );
        }
    }

    private void validateFile(
            String originalFileName,
            String contentType,
            long fileSize,
            byte[] content
    ) {
        boolean invalidFile = originalFileName == null
                || originalFileName.isBlank()
                || contentType == null
                || !ALLOWED_CONTENT_TYPES.contains(contentType)
                || fileSize <= 0
                || fileSize > MAX_FILE_SIZE
                || content == null
                || content.length == 0
                || fileSize != content.length;

        if (invalidFile) {
            throw new ValidationException(LectureErrorCode.LECTURE_MATERIAL_INVALID_FILE);
        }
    }

    private String sanitizeFileName(String originalFileName) {
        return originalFileName
                .replace("\\", "_")
                .replace("/", "_")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
