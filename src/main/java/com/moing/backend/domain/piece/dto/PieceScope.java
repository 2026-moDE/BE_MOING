package com.moing.backend.domain.piece.dto;

/**
 * 조각 목록 조회 범위(scope 쿼리 파라미터).
 *
 * <p>보드 화면은 전체 조각을 다 그리지 않고 "지금 붙어 있는 것"만 그린다. 목록 화면과
 * 보드 화면이 같은 API를 쓰되 이 값으로 갈린다.
 */
public enum PieceScope {

    /** 전체. scope를 생략하면 이 값이다 */
    ALL,

    /** 보드에 실제로 붙어 있는 것 — 고정 조각과 최근 조각 */
    BOARD
}
