package com.wanted.codebombalms.problems.dataset.infrastructure.storage;

import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorage;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorageProperties;
import com.wanted.codebombalms.problems.dataset.application.command.UploadProblemDatasetCommand;
import com.wanted.codebombalms.problems.dataset.application.port.StoreDatasetFilePort;
import com.wanted.codebombalms.problems.dataset.application.service.DatasetMetadataExtractor;
import com.wanted.codebombalms.problems.dataset.domain.model.StoredDatasetFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Primary
@Component
@Slf4j
@RequiredArgsConstructor
public class S3DatasetFileStorage implements StoreDatasetFilePort {

    private static final String CSV_CONTENT_TYPE = "text/csv";

    private final ObjectStorageProperties properties;
    private final ObjectStorage objectStorage;
    private final DatasetMetadataExtractor metadataExtractor;

    @Override
    public StoredDatasetFile store(UploadProblemDatasetCommand command) throws IOException {
        String originalFileName = sanitizeFileName(command.originalFileName());
        String storedFileName = UUID.randomUUID() + "_" + originalFileName;
        String objectName = ObjectStorageProperties.objectKey(
                properties.getDatasetPrefix(),
                storedFileName
        );

        objectStorage.put(objectName, CSV_CONTENT_TYPE, command.content());

        String metadata = metadataExtractor.extract(command.content());

        return StoredDatasetFile.create(
                originalFileName,
                storedFileName,
                objectStorage.publicUrl(objectName),
                objectName,
                command.fileSize(),
                metadata
        );
    }

    @Override
    public void delete(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return;
        }

        try {
            objectStorage.delete(filePath);
        } catch (Exception e) {
            log.warn("S3에서 파일 삭제를 실패했습니다. filePath={}", filePath, e);
        }
    }

    private String sanitizeFileName(String originalFileName) {
        return originalFileName
                .replace("\\", "_")
                .replace("/", "_");
    }
}
