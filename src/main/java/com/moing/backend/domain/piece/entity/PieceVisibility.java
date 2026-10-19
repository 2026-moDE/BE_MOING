package com.moing.backend.domain.piece.entity;

/**
 * 조각 공개 범위
 * PUBLIC  - 다른 사용자가 내 보드를 볼 때 보인다 (친구가 아니어도 보인다)
 * PRIVATE - 나만 본다
 *
 * <p>리뷰의 {@link com.moing.backend.domain.review.entity.Visibility}(PUBLIC/FRIENDS)와는
 * 의미가 다르다. 리뷰는 "누구에게 공개할지"를 고르는 값이고, 조각은 보드에 올린 것을
 * 남에게 보일지 말지만 고르므로 두 척도를 섞지 않는다.
 *
 * <p>그래서 타인 보드 조회에 친구 조건을 두지 않는다 — 공개 여부를 정하는 값은 이것뿐이다.
 */
public enum PieceVisibility {
    PUBLIC,
    PRIVATE
}
