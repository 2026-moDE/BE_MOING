package com.moing.backend.global.response;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 공통 API 응답 래퍼
 * 모든 API 응답을 success, message, data 구조로 통일한다.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonPropertyOrder({"success", "message", "data"})
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    // 데이터만 반환하는 성공 응답 생성
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, null, data);
    }

    // 메시지와 데이터를 함께 반환하는 성공 응답 생성
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    // 에러 메시지를 포함한 실패 응답 생성
    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
