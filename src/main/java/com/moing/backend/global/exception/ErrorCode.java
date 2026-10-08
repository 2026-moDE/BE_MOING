package com.moing.backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 전역 에러 코드 정의
 * HTTP 상태 코드와 에러 메시지를 함께 관리한다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "권한이 없습니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    DUPLICATE(HttpStatus.CONFLICT, "이미 존재합니다"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type은 application/json이어야 합니다"),
    MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문의 형식이 올바르지 않습니다"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),

    // --- 온보딩 및 유저 관련 에러 코드 추가 ---
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다"),
    ALREADY_ONBOARDED(HttpStatus.CONFLICT, "이미 온보딩을 완료한 유저입니다"),

    // --- 유저 조회 관련 ---
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다"),

    // --- 친구 관련 ---
    SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신에게 친구 요청을 보낼 수 없습니다"),
    ALREADY_FOLLOWING(HttpStatus.CONFLICT, "이미 친구 요청 중이거나 친구 상태입니다"),

    // --- 댓글 관련 ---
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없습니다"),
    NOT_FRIEND_REVIEW(HttpStatus.FORBIDDEN, "친구 공개 리뷰에는 친구만 접근할 수 있습니다"),
    SECRET_COMMENT_NOT_ALLOWED(HttpStatus.FORBIDDEN, "친구 공개 리뷰에만 비밀 댓글을 작성할 수 있습니다"),
    NESTED_REPLY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "답글에는 답글을 작성할 수 없습니다"),
    DELETED_COMMENT_REPLY_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "삭제된 댓글에는 답글을 작성할 수 없습니다"),

    // --- 이모지 반응 관련 ---
    DUPLICATE_REACTION(HttpStatus.CONFLICT, "이미 추가한 이모지입니다"),
    REACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "취소할 이모지 반응이 없습니다"),
    SELF_REACTION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "자신의 리뷰에는 이모지를 남길 수 없습니다"),

    // --- 조각 관련 ---
    PIECE_NOT_FOUND(HttpStatus.NOT_FOUND, "조각을 찾을 수 없습니다"),
    DUPLICATE_PIECE(HttpStatus.CONFLICT, "이미 조각으로 만든 리뷰입니다"),
    NOT_OWN_PIECE(HttpStatus.FORBIDDEN, "본인의 조각만 수정할 수 있습니다"),
    INVALID_PIECE_POSITION(HttpStatus.BAD_REQUEST, "조각 위치 값이 올바르지 않습니다"),

    // --- 관리자 관련 ---
    ADMIN_LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다");

    private final HttpStatus status;
    private final String message;
}
