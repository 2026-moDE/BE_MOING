package com.moing.backend.global.exception;

import com.moing.backend.global.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 전역 예외 처리기
 * 컨트롤러에서 발생하는 모든 예외를 일관된 형식으로 처리한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 로직 예외 처리 - ErrorCode에 정의된 상태코드와 메시지를 반환
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode.getMessage()));
    }

    // 요청 값 유효성 검사 실패 처리 - 첫 번째 필드 에러 메시지를 반환
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        String message = fieldError.getDefaultMessage();
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(message));
    }

    // Content-Type이 application/json이 아닌 경우 - 415 반환
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeException(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity
                .status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus())
                .body(ApiResponse.error(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage()));
    }

    // 본문이 없거나 JSON 형식이 잘못된 경우 - 400 반환
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadableException(HttpMessageNotReadableException e) {
        return ResponseEntity
                .status(ErrorCode.MALFORMED_REQUEST_BODY.getStatus())
                .body(ApiResponse.error(ErrorCode.MALFORMED_REQUEST_BODY.getMessage()));
    }

    // 필수 쿼리 파라미터가 누락된 경우 - 400 반환
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameterException(MissingServletRequestParameterException e) {
        return ResponseEntity
                .status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(e.getParameterName() + " 파라미터가 필요합니다"));
    }

    // 처리되지 않은 모든 예외 처리 - 500 에러를 반환하고 스택 트레이스를 출력
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        e.printStackTrace();
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
