package com.wanted.codebombalms.global.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 파일 저장소(S3) 설정. 버킷 하나를 prefix 로 나눠 쓴다.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class ObjectStorageProperties {

    private String bucket;
    private String region = "ap-northeast-2";
    private String datasetPrefix = "problem_dataset";
    private String badgeImagePrefix = "badge_image";
    private String courseThumbnailPrefix = "course_thumbnail_images";
    private String lectureMaterialPrefix = "lecture_materials";

    /** 앞뒤 슬래시를 떼어 낸 prefix. null 이면 빈 문자열. */
    public static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return "";
        }
        return prefix.replaceAll("^/+", "").replaceAll("/+$", "");
    }

    /** prefix 가 비어 있으면 파일명만, 아니면 prefix/파일명. */
    public static String objectKey(String prefix, String fileName) {
        String normalized = normalizePrefix(prefix);
        return normalized.isBlank() ? fileName : normalized + "/" + fileName;
    }
}
