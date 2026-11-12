package com.wanted.codebombalms.problems.dataset.infrastructure.persistence;

import com.wanted.codebombalms.global.domain.common.error.exception.ExternalServiceException;
import com.wanted.codebombalms.global.domain.common.error.exception.NotFoundException;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorage;
import com.wanted.codebombalms.problems.dataset.application.port.GenerateDatasetAccessUrlPort;
import com.wanted.codebombalms.problems.dataset.application.port.GenerateDatasetDownloadUrlPort;
import com.wanted.codebombalms.problems.exception.ProblemErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class S3DatasetAccessUrlAdapter implements
        GenerateDatasetAccessUrlPort,
        GenerateDatasetDownloadUrlPort {

    private static final Duration DOWNLOAD_URL_DURATION = Duration.ofMinutes(1);
    private static final Duration RUNNER_URL_DURATION = Duration.ofMinutes(30);

    private final ObjectStorage objectStorage;

    // Runner 접근 URL
    @Override
    public String generate(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new NotFoundException(
                    ProblemErrorCode.PROBLEM_DATASET_NOT_FOUND
            );
        }

        try {
            return objectStorage.presignedGetUrl(filePath, RUNNER_URL_DURATION);
        } catch (Exception e) {
            throw new ExternalServiceException(
                    ProblemErrorCode.PROBLEM_DATASET_ACCESS_URL_FAILED,
                    e
            );
        }
    }

    // 사용자 다운로드 URL
    @Override
    public String generate(String filePath, String originalFileName) {
        if (filePath == null || filePath.isBlank()
                || originalFileName == null
                || originalFileName.isBlank()) {
            throw new NotFoundException(
                    ProblemErrorCode.PROBLEM_DATASET_NOT_FOUND
            );
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
                    ProblemErrorCode.PROBLEM_DATASET_ACCESS_URL_FAILED,
                    e
            );
        }
    }
}
