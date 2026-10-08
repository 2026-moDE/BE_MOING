package com.moing.backend.domain.piece.service;

import com.moing.backend.domain.follow.entity.FollowStatus;
import com.moing.backend.domain.follow.repository.FollowRepository;
import com.moing.backend.domain.piece.dto.PieceCreateRequest;
import com.moing.backend.domain.piece.dto.PieceCreateResponse;
import com.moing.backend.domain.piece.dto.PieceListResponse;
import com.moing.backend.domain.piece.dto.PiecePositionUpdateRequest;
import com.moing.backend.domain.piece.dto.PiecePositionUpdateResponse;
import com.moing.backend.domain.piece.dto.PieceUpdateRequest;
import com.moing.backend.domain.piece.entity.Piece;
import com.moing.backend.domain.piece.entity.PieceVisibility;
import com.moing.backend.domain.piece.repository.PieceRepository;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import com.moing.backend.domain.user.repository.UserRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PieceService {

    // position_x/y는 보드 크기에 대한 비율이라 0~1을 벗어날 수 없다
    private static final BigDecimal MIN_POSITION = BigDecimal.ZERO;
    private static final BigDecimal MAX_POSITION = BigDecimal.ONE;
    // DECIMAL(5,4) 컬럼과 같은 자리수
    private static final int POSITION_SCALE = 4;
    // 손으로 살짝 비뚼 정도까지만 허용한다 (단위: 도)
    private static final int MIN_ROTATION = -16;
    private static final int MAX_ROTATION = 16;

    private final PieceRepository pieceRepository;
    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    /**
     * 조각 등록.
     *
     * <p>리뷰가 없으면 NOT_FOUND, 남의 리뷰면 FORBIDDEN, 이미 조각이 있으면 DUPLICATE_PIECE다.
     * 누끼 PNG 업로드는 프론트가 S3에 먼저 끝내고 그 URL만 보낸다.
     */
    @Transactional
    public PieceCreateResponse createPiece(Long userId, PieceCreateRequest request) {
        Review review = reviewRepository.findById(request.reviewId())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (!review.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        if (pieceRepository.existsByReviewId(review.getId())) {
            throw new CustomException(ErrorCode.DUPLICATE_PIECE);
        }

        Piece saved = pieceRepository.save(Piece.builder()
                .userId(userId)
                .reviewId(review.getId())
                .imageUrl(request.imageUrl())
                .name(normalizeName(request.name()))
                .visibility(request.visibility())
                .build());

        return new PieceCreateResponse(saved.getId());
    }

    /** 내 조각 전체 (원본 리뷰 작성 시각 내림차순) */
    public PieceListResponse getMyPieces(Long userId) {
        return toListResponse(pieceRepository.findByUserId(userId), true);
    }

    /**
     * 친구 조각 목록.
     *
     * <p>전체 공개 조각만 실리고 visibility는 응답에서 빠진다. 친구가 아니면 FORBIDDEN이다.
     * 자기 id로 조회하는 경우는 친구 관계가 없어도 통과시킨다 (타인 리뷰 조회와 같은 규칙).
     */
    public PieceListResponse getUserPieces(Long viewerId, Long targetUserId) {
        if (!userRepository.existsById(targetUserId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (!viewerId.equals(targetUserId) && !isFriend(viewerId, targetUserId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return toListResponse(
                pieceRepository.findByUserIdAndVisibility(targetUserId, PieceVisibility.PUBLIC), false);
    }

    /** 조각 수정. 보낸 필드만 바뀐다. */
    @Transactional
    public void updatePiece(Long userId, Long pieceId, PieceUpdateRequest request) {
        Piece piece = findOwnPiece(userId, pieceId);

        if (request.name() != null) {
            piece.updateName(normalizeName(request.name()));
        }

        if (request.visibility() != null) {
            piece.updateVisibility(request.visibility());
        }
    }

    /** 조각 삭제. S3 객체는 남겨둔다 (원본 리뷰 사진과 같은 정책). */
    @Transactional
    public void deletePiece(Long userId, Long pieceId) {
        pieceRepository.delete(findOwnPiece(userId, pieceId));
    }

    /**
     * 보드 배치 벌크 저장.
     *
     * <p>한 트랜잭션이라 남의 조각이 하나라도 섞여 있거나 값이 범위를 벗어나면 전체가 롤백된다.
     * 같은 조각이 두 번 실리면 뒤 항목이 이긴다.
     */
    @Transactional
    public PiecePositionUpdateResponse updatePositions(Long userId, PiecePositionUpdateRequest request) {
        List<PiecePositionUpdateRequest.PositionItem> items = request.positions();
        if (items.isEmpty()) {
            return new PiecePositionUpdateResponse(0);
        }

        List<Long> pieceIds = items.stream()
                .map(PiecePositionUpdateRequest.PositionItem::pieceId)
                .distinct()
                .toList();
        Map<Long, Piece> pieceMap = pieceRepository.findAllById(pieceIds).stream()
                .collect(Collectors.toMap(Piece::getId, Function.identity()));

        for (PiecePositionUpdateRequest.PositionItem item : items) {
            Piece piece = pieceMap.get(item.pieceId());
            // 없는 id도 남의 조각과 같이 취급한다. 존재 여부를 알려줄 이유가 없다
            if (piece == null || !piece.getUserId().equals(userId)) {
                throw new CustomException(ErrorCode.NOT_OWN_PIECE);
            }
            piece.place(
                    normalizePosition(item.positionX()),
                    normalizePosition(item.positionY()),
                    toRotation(item.rotation()));
        }

        return new PiecePositionUpdateResponse(pieceIds.size());
    }

    private Piece findOwnPiece(Long userId, Long pieceId) {
        Piece piece = pieceRepository.findById(pieceId)
                .orElseThrow(() -> new CustomException(ErrorCode.PIECE_NOT_FOUND));

        if (!piece.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_OWN_PIECE);
        }

        return piece;
    }

    // 서로 친구(ACCEPTED)인지 확인
    private boolean isFriend(Long userId, Long targetUserId) {
        return followRepository.existsByFollowerIdAndFollowingIdAndStatusIn(
                userId, targetUserId, List.of(FollowStatus.ACCEPTED));
    }

    // 빈 문자열은 null로 정규화 (이름 제거로 동작)
    private String normalizeName(String name) {
        return name != null && !name.isBlank() ? name : null;
    }

    private BigDecimal normalizePosition(BigDecimal value) {
        if (value == null
                || value.compareTo(MIN_POSITION) < 0
                || value.compareTo(MAX_POSITION) > 0) {
            throw new CustomException(ErrorCode.INVALID_PIECE_POSITION);
        }
        // DECIMAL(5,4)라 저장되면서 어차피 반올림된다. 저장값과 다음 조회 응답이 어긋나지 않게 미리 맞춘다
        return value.setScale(POSITION_SCALE, RoundingMode.HALF_UP);
    }

    private short toRotation(Integer rotation) {
        // PUT이라 보낸 값으로 전체를 덮어쓴다. 생략은 "회전 없음"으로 본다
        int value = rotation != null ? rotation : 0;
        if (value < MIN_ROTATION || value > MAX_ROTATION) {
            throw new CustomException(ErrorCode.INVALID_PIECE_POSITION);
        }
        return (short) value;
    }

    /**
     * 조각에 원본 리뷰의 작성 시각과 장소를 붙여 내려준다.
     *
     * <p>리뷰·장소는 연관관계 없이 id만 들고 있어 N+1을 피하려 일괄 조회한다.
     * 리뷰가 ARCHIVED여도 조각은 그대로 보인다. 정렬 기준은 응답에 실리는 created_at,
     * 즉 리뷰 작성 시각이다 (조각을 만든 순서가 아니다).
     */
    private PieceListResponse toListResponse(List<Piece> pieces, boolean includeVisibility) {
        if (pieces.isEmpty()) {
            return new PieceListResponse(0, List.of());
        }

        Map<Long, Review> reviewMap = reviewRepository.findAllById(
                        pieces.stream().map(Piece::getReviewId).distinct().toList()).stream()
                .collect(Collectors.toMap(Review::getId, Function.identity()));

        List<Long> placeIds = reviewMap.values().stream().map(Review::getPlaceId).distinct().toList();
        Map<Long, Place> placeMap = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        List<PieceListResponse.PieceItem> items = pieces.stream()
                // 리뷰를 지우면 조각도 함께 지우므로 정상적으로는 비지 않는다.
                // 그래도 남은 고아 조각은 시각·장소를 채울 수 없어 목록에서 뺀다
                .filter(p -> reviewMap.containsKey(p.getReviewId()))
                .map(p -> {
                    Review review = reviewMap.get(p.getReviewId());
                    Place place = placeMap.get(review.getPlaceId());
                    PieceListResponse.PlaceInfo placeInfo = place != null
                            ? new PieceListResponse.PlaceInfo(place.getId(), place.getName())
                            : new PieceListResponse.PlaceInfo(review.getPlaceId(), null);

                    return new PieceListResponse.PieceItem(
                            p.getId(), p.getReviewId(), p.getImageUrl(), p.getName(),
                            includeVisibility ? p.getVisibility() : null,
                            p.getPositionX(), p.getPositionY(), p.getRotation(),
                            placeInfo, review.getCreatedAt());
                })
                .sorted(Comparator.comparing(PieceListResponse.PieceItem::createdAt).reversed()
                        .thenComparing(Comparator.comparing(PieceListResponse.PieceItem::id).reversed()))
                .toList();

        return new PieceListResponse(items.size(), items);
    }
}
