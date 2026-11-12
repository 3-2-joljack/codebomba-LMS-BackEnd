package com.wanted.codebombalms.lecture.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wanted.codebombalms.global.domain.common.error.exception.ExternalServiceException;
import com.wanted.codebombalms.global.domain.common.error.exception.ValidationException;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorage;
import com.wanted.codebombalms.global.infrastructure.storage.ObjectStorageProperties;
import com.wanted.codebombalms.lecture.application.port.LectureMaterialStoragePort.StoredLectureMaterial;
import com.wanted.codebombalms.lecture.domain.exception.LectureErrorCode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("S3LectureMaterialStorageAdapter test")
class S3LectureMaterialStorageAdapterTest {

    private final ObjectStorage objectStorage = mock(ObjectStorage.class);
    private final S3LectureMaterialStorageAdapter adapter =
            new S3LectureMaterialStorageAdapter(new ObjectStorageProperties(), objectStorage);

    @Test
    void upload_preservesOriginalKoreanFileNameForDisplayAndSanitizesStoredFileName() {
        byte[] content = "lecture material".getBytes(StandardCharsets.UTF_8);
        String originalFileName = "자료 1강.pdf";

        StoredLectureMaterial storedMaterial = adapter.upload(
                originalFileName,
                "application/pdf",
                content.length,
                content
        );

        verify(objectStorage).put(eq(storedMaterial.filePath()), eq("application/pdf"), eq(content));
        assertThat(storedMaterial.originalFileName()).isEqualTo(originalFileName);
        assertThat(storedMaterial.storedFileName()).endsWith("___1_.pdf");
        assertThat(storedMaterial.filePath()).startsWith("lecture_materials/");
    }

    @Test
    void upload_throwsValidation_whenOriginalFileNameIsBlank() {
        byte[] content = "lecture material".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.upload(
                " ",
                "application/pdf",
                content.length,
                content
        ))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(LectureErrorCode.LECTURE_MATERIAL_INVALID_FILE);
    }

    @Test
    void upload_throwsValidation_whenContentTypeIsNotAllowed() {
        byte[] content = "lecture material".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.upload(
                "guide.exe",
                "application/x-msdownload",
                content.length,
                content
        ))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(LectureErrorCode.LECTURE_MATERIAL_INVALID_FILE);
    }

    @Test
    void upload_throwsValidation_whenFileSizeExceedsLimit() {
        byte[] content = "lecture material".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> adapter.upload(
                "guide.pdf",
                "application/pdf",
                20 * 1024 * 1024 + 1L,
                content
        ))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(LectureErrorCode.LECTURE_MATERIAL_INVALID_FILE);
    }

    @Test
    void upload_throwsValidation_whenContentIsEmpty() {
        byte[] content = new byte[0];

        assertThatThrownBy(() -> adapter.upload(
                "guide.pdf",
                "application/pdf",
                content.length,
                content
        ))
                .isInstanceOf(ValidationException.class)
                .extracting("errorCode")
                .isEqualTo(LectureErrorCode.LECTURE_MATERIAL_INVALID_FILE);
    }

    @Test
    void upload_throwsExternalServiceException_whenStoragePutFails() {
        byte[] content = "lecture material".getBytes(StandardCharsets.UTF_8);
        doThrow(new RuntimeException("upload failed"))
                .when(objectStorage).put(anyString(), anyString(), any(byte[].class));

        assertThatThrownBy(() -> adapter.upload(
                "guide.pdf",
                "application/pdf",
                content.length,
                content
        ))
                .isInstanceOf(ExternalServiceException.class)
                .extracting("errorCode")
                .isEqualTo(LectureErrorCode.LECTURE_MATERIAL_UPLOAD_FAILED);
    }

    @Test
    void generateDownloadUrl_requestsAttachmentDispositionWithOriginalFileName() {
        when(objectStorage.presignedDownloadUrl(anyString(), any(Duration.class), anyString()))
                .thenReturn("https://signed.example/guide.pdf");

        String url = adapter.generateDownloadUrl("lecture_materials/abc_guide.pdf", "가이드.pdf");

        assertThat(url).isEqualTo("https://signed.example/guide.pdf");
        verify(objectStorage).presignedDownloadUrl(
                eq("lecture_materials/abc_guide.pdf"),
                eq(Duration.ofMinutes(1)),
                org.mockito.ArgumentMatchers.startsWith("attachment;")
        );
    }

    @Test
    void generateDownloadUrl_throwsExternalServiceException_whenPresignFails() {
        when(objectStorage.presignedDownloadUrl(anyString(), any(Duration.class), anyString()))
                .thenThrow(new RuntimeException("presign failed"));

        assertThatThrownBy(() -> adapter.generateDownloadUrl("lecture_materials/a.pdf", "a.pdf"))
                .isInstanceOf(ExternalServiceException.class)
                .extracting("errorCode")
                .isEqualTo(LectureErrorCode.LECTURE_MATERIAL_DOWNLOAD_URL_FAILED);
    }
}
