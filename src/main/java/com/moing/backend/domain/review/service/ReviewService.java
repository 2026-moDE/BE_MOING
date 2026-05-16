package com.moing.backend.domain.review.service;

import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.dto.ReviewCreateRequest;
import com.moing.backend.domain.review.dto.ReviewCreateResponse;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;

    // 리뷰 작성
    @Transactional
    public ReviewCreateResponse createReview(Long userId, ReviewCreateRequest request) {
        if (!placeRepository.existsById(request.placeId())) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }

        Review review = Review.builder()
                .userId(userId)
                .placeId(request.placeId())
                .congestionLevel(request.congestionLevel())
                .quickTag(request.quickTag())
                .comment(request.comment())
                .imageUrl(request.imageUrl())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();

        return ReviewCreateResponse.from(reviewRepository.save(review));
    }
}
