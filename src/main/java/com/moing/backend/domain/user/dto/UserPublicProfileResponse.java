package com.moing.backend.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

/**
 * 타인 프로필 조회 응답.
 *
 * <p>이메일은 담지 않는다. 본인 조회({@link UserProfileResponse})와 달리
 * 남에게 보여주는 화면이라 연락처가 노출될 이유가 없다.
 */
@JsonPropertyOrder({"id", "nickname", "profile_image_url", "profile_url",
        "friend_count", "review_count", "is_friend", "friend_status", "reviews"})
public record UserPublicProfileResponse(
        Long id,
        String nickname,
        @JsonProperty("profile_image_url") String profileImageUrl,
        @JsonProperty("profile_url") String profileUrl,
        @JsonProperty("friend_count") long friendCount,
        // 조회자에게 보이는 리뷰만 센다. reviews에 담기는 것과 같은 기준이라
        // 목록 길이와 개수가 어긋나지 않는다
        @JsonProperty("review_count") long reviewCount,
        @JsonProperty("is_friend") boolean isFriend,
        // NONE / PENDING / ACCEPTED
        @JsonProperty("friend_status") String friendStatus,
        List<UserReviewItem> reviews
) {}
