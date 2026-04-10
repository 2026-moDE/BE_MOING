package com.moing.backend.global.exception;

import lombok.Getter;

/**
 * 커스텀 예외 클래스
 * ErrorCode를 기반으로 비즈니스 예외를 표현한다.
 */
@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    // ErrorCode를 받아 예외 메시지를 설정
    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
