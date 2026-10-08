package com.moing.backend.domain.piece.entity;

/**
 * 조각 공개 범위
 * PUBLIC  - 친구가 내 보드를 볼 때 보인다
 * PRIVATE - 나만 본다
 *
 * <p>리뷰의 {@link com.moing.backend.domain.review.entity.Visibility}(PUBLIC/FRIENDS)와는
 * 의미가 다르다. 리뷰는 "누구에게 공개할지"를 고르는 값이고, 조각은 보드에 올린 것을
 * 친구에게 보일지 말지만 고르므로 두 척도를 섞지 않는다.
 */
public enum PieceVisibility {
    PUBLIC,
    PRIVATE
}
