package com.wanted.codebombalms.global.infrastructure.storage;

import java.io.InputStream;
import java.time.Duration;

/**
 * 버킷 하나에 대한 객체 저장소 연산. 도메인별 저장 어댑터가 공유한다.
 */
public interface ObjectStorage {

    void put(String key, String contentType, byte[] content);

    void put(String key, String contentType, InputStream content, long contentLength);

    void delete(String key);

    /** 일정 시간만 유효한 읽기 URL. */
    String presignedGetUrl(String key, Duration duration);

    /** 다운로드 파일명을 지정한 읽기 URL. */
    String presignedDownloadUrl(String key, Duration duration, String contentDisposition);

    /** 서명 없는 공개 URL (공개 읽기가 허용된 prefix 에만 쓴다). */
    String publicUrl(String key);

    /** 공개 URL 에서 객체 키를 꺼낸다. 이 버킷의 URL 이 아니면 null. */
    String keyFromPublicUrl(String url);
}
