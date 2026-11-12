package com.wanted.codebombalms.problems.generation.infrastructure.storage;

import com.wanted.codebombalms.global.domain.common.error.exception.ExternalServiceException;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorage;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorageProperties;
import com.wanted.codebombalms.problems.exception.ProblemErrorCode;
import com.wanted.codebombalms.problems.generation.application.command.StoreProblemSetDraftDatasetCommand;
import com.wanted.codebombalms.problems.generation.application.port.DeleteProblemSetDraftDatasetPort;
import com.wanted.codebombalms.problems.generation.application.port.GenerateProblemSetDraftDatasetUrlPort;
import com.wanted.codebombalms.problems.generation.application.port.StoreProblemSetDraftDatasetPort;
import com.wanted.codebombalms.problems.generation.domain.StoredProblemSetDraftDataset;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class S3ProblemSetDraftDatasetStorageAdapter implements
        StoreProblemSetDraftDatasetPort,
        GenerateProblemSetDraftDatasetUrlPort,
        DeleteProblemSetDraftDatasetPort {

    private static final String CSV_CONTENT_TYPE = "text/csv";
    private static final String DRAFT_DIRECTORY = "drafts";
    private static final Duration SIGNED_URL_DURATION = Duration.ofMinutes(30);

    private final ObjectStorageProperties properties;
    private final ObjectStorage objectStorage;

    @Override
    public StoredProblemSetDraftDataset store(StoreProblemSetDraftDatasetCommand command) {
        String originalFileName = sanitizeFileName(command.originalFileName());
        String storedFileName = command.draftToken() + "_" + originalFileName;
        String objectName = buildObjectName(command, storedFileName);

        try {
            try (InputStream content = command.content()) {
                objectStorage.put(objectName, CSV_CONTENT_TYPE, content, command.fileSize());
            }

            return new StoredProblemSetDraftDataset(
                    originalFileName,
                    storedFileName,
                    objectName,
                    command.fileSize()
            );
        } catch (Exception e) {
            throw new ExternalServiceException(
                    ProblemErrorCode.PROBLEM_DATASET_UPLOAD_FAILED,
                    e
            );
        }
    }

    @Override
    public String generate(String objectName) {
        try {
            return objectStorage.presignedGetUrl(objectName, SIGNED_URL_DURATION);
        } catch (Exception e) {
            log.error(
                    "event=problem_set_draft_dataset_signed_url_failed bucket={} objectName={} exceptionType={}",
                    properties.getBucket(),
                    objectName,
                    e.getClass().getSimpleName(),
                    e
            );

            throw new ExternalServiceException(
                    ProblemErrorCode.PROBLEM_DATASET_ACCESS_URL_FAILED,
                    e
            );
        }
    }

    @Override
    public void delete(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }

        try {
            objectStorage.delete(objectName);
        } catch (Exception e) {
            log.warn(
                    "event=problem_set_draft_dataset_delete_failed objectName={}",
                    objectName,
                    e
            );
        }
    }

    private String buildObjectName(
            StoreProblemSetDraftDatasetCommand command,
            String storedFileName
    ) {
        String prefix = ObjectStorageProperties.normalizePrefix(properties.getDatasetPrefix());

        return prefix
                + "/"
                + DRAFT_DIRECTORY
                + "/"
                + command.operatorId()
                + "/"
                + command.draftToken()
                + "/"
                + storedFileName;
    }

    private String sanitizeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            return "dataset.csv";
        }

        return originalFileName
                .replace("\\", "_")
                .replace("/", "_");
    }
}
