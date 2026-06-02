package com.moing.backend.domain.review.service;

import com.moing.backend.domain.place.entity.Place;
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
        Long placeId = resolveOrCreatePlaceId(request);

        Review review = Review.builder()
                .userId(userId)
                .placeId(placeId)
                .congestionLevel(request.congestionLevel())
                .quickTag(request.quickTag())
                .comment(request.comment())
                .imageUrl(request.imageUrl())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();

        return ReviewCreateResponse.from(reviewRepository.save(review));
    }

    // placeId가 있으면 존재 확인, 없으면 새 장소 생성 후 id 반환
    private Long resolveOrCreatePlaceId(ReviewCreateRequest request) {
        if (request.placeId() != null) {
            if (!placeRepository.existsById(request.placeId())) {
                throw new CustomException(ErrorCode.NOT_FOUND);
            }
            return request.placeId();
        }

        if (request.placeName() == null || request.placeAddress() == null
                || request.placeLatitude() == null || request.placeLongitude() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Place newPlace = Place.builder()
                .name(request.placeName())
                .address(request.placeAddress())
                .latitude(request.placeLatitude())
                .longitude(request.placeLongitude())
                .category(request.placeCategory())
                .source("USER")
                .build();

        return placeRepository.save(newPlace).getId();
    }
}
