package com.moing.backend.domain.piece.service;

import com.moing.backend.domain.block.service.BlockService;
import com.moing.backend.domain.piece.dto.PieceCreateRequest;
import com.moing.backend.domain.piece.dto.PieceCreateResponse;
import com.moing.backend.domain.piece.dto.PieceListResponse;
import com.moing.backend.domain.piece.dto.PiecePinUpdateRequest;
import com.moing.backend.domain.piece.dto.PiecePinUpdateResponse;
import com.moing.backend.domain.piece.dto.PiecePositionUpdateRequest;
import com.moing.backend.domain.piece.dto.PiecePositionUpdateResponse;
import com.moing.backend.domain.piece.dto.PieceScope;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    // 배율. 원래 크기의 절반~두 배까지만 허용한다
    private static final BigDecimal MIN_SCALE = new BigDecimal("0.5");
    private static final BigDecimal MAX_SCALE = new BigDecimal("2.0");
    // DECIMAL(3,2) 컬럼과 같은 자리수
    private static final int SCALE_SCALE = 2;
    // board scope가 "최근"으로 보는 기간. 다른 화면(친구 피드·검색)이 쓰는 72h 기준과 같은 값이다.
    // 공용 상수는 아직 없고 화면마다 제 상수를 들고 있어 여기도 따로 둔다
    private static final int BOARD_RECENT_HOURS = 72;

    private final PieceRepository pieceRepository;
    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final BlockService blockService;
    private final Clock clock;

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

    /**
     * 내 조각 목록 (원본 리뷰 작성 시각 내림차순).
     *
     * <p>scope가 board면 보드에 붙어 있는 것(고정 + 최근 72h)만 실린다. 생략하면 전체다.
     */
    public PieceListResponse getMyPieces(Long userId, String scope) {
        return toListResponse(pieceRepository.findByUserId(userId), true, toScope(scope));
    }

    /**
     * 타인 조각 목록.
     *
     * <p>전체 공개 조각만 실리고 visibility는 응답에서 빠진다.
     *
     * <p>친구 여부를 따지지 않는다 — 접근을 가르는 값은 조각 자신의 공개 범위다.
     * PRIVATE 조각은 누구에게도 안 보이고, PUBLIC 조각은 누구에게나 보인다.
     * 친구 관계를 한 겹 더 두면 "전체 공개로 올렸는데 친구만 본다"가 되어 두 척도가 겹친다.
     *
     * <p>차단 관계가 있으면 403이다 (타인 프로필·리뷰 목록과 같은 규칙).
     *
     * <p>scope는 PUBLIC만 추린 뒤에 적용한다. 남의 PRIVATE 고정 조각이 board에 끼어들 일은 없다.
     */
    public PieceListResponse getUserPieces(Long viewerId, Long targetUserId, String scope) {
        if (!userRepository.existsById(targetUserId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        blockService.validateNotBlocked(viewerId, targetUserId);

        return toListResponse(
                pieceRepository.findByUserIdAndVisibility(targetUserId, PieceVisibility.PUBLIC),
                false, toScope(scope));
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
                    toRotation(item.rotation()),
                    toScale(item.scale()));
        }

        return new PiecePositionUpdateResponse(pieceIds.size());
    }

    /**
     * 고정 슬롯 저장.
     *
     * <p>보낸 목록이 전체를 덮어쓴다 — 기존 고정을 전부 푼 뒤 배열 순서대로 1부터 다시 매긴다.
     * 빈 배열이면 전부 해제다.
     *
     * <p>소유 검증을 먼저 끝내고 나서 손을 대므로, 남의 조각이 하나라도 섞이면 기존 고정은
     * 그대로 남는다 (한 트랜잭션이라 중간에 끊겨도 전체가 롤백된다).
     *
     * <p>공개 범위는 보지 않는다. PRIVATE 조각도 내 보드에는 고정할 수 있고,
     * 남의 목록에서는 어차피 PUBLIC만 실린다.
     */
    @Transactional
    public PiecePinUpdateResponse updatePins(Long userId, PiecePinUpdateRequest request) {
        List<Long> pieceIds = request.pieceIds();
        if (pieceIds.size() > Piece.MAX_PINS
                || pieceIds.stream().distinct().count() != pieceIds.size()) {
            throw new CustomException(ErrorCode.INVALID_PIECE_PIN);
        }

        Map<Long, Piece> pieceMap = pieceRepository.findAllById(pieceIds).stream()
                .collect(Collectors.toMap(Piece::getId, Function.identity()));
        List<Piece> targets = pieceIds.stream()
                .map(pieceId -> {
                    Piece piece = pieceMap.get(pieceId);
                    // 배치 저장과 같은 규칙. 없는 id도 남의 조각과 같이 취급한다
                    if (piece == null || !piece.getUserId().equals(userId)) {
                        throw new CustomException(ErrorCode.NOT_OWN_PIECE);
                    }
                    return piece;
                })
                .toList();

        pieceRepository.findByUserIdAndPinOrderIsNotNull(userId).forEach(Piece::unpin);

        List<PiecePinUpdateResponse.PinItem> pins = new ArrayList<>();
        short order = 1;
        for (Piece piece : targets) {
            piece.pin(order);
            pins.add(new PiecePinUpdateResponse.PinItem(piece.getId(), order));
            order++;
        }

        return new PiecePinUpdateResponse(pins.size(), pins);
    }

    private Piece findOwnPiece(Long userId, Long pieceId) {
        Piece piece = pieceRepository.findById(pieceId)
                .orElseThrow(() -> new CustomException(ErrorCode.PIECE_NOT_FOUND));

        if (!piece.getUserId().equals(userId)) {
            throw new CustomException(ErrorCode.NOT_OWN_PIECE);
        }

        return piece;
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

    private BigDecimal toScale(BigDecimal scale) {
        // rotation과 같은 규칙. 생략은 "원래 크기"로 본다
        if (scale == null) {
            return Piece.DEFAULT_SCALE;
        }
        if (scale.compareTo(MIN_SCALE) < 0 || scale.compareTo(MAX_SCALE) > 0) {
            throw new CustomException(ErrorCode.INVALID_PIECE_POSITION);
        }
        return scale.setScale(SCALE_SCALE, RoundingMode.HALF_UP);
    }

    // 생략은 전체 조회다. 대소문자는 가리지 않고, 모르는 값은 조용히 전체로 넘기지 않고 400으로 끊는다
    private PieceScope toScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return PieceScope.ALL;
        }
        try {
            return PieceScope.valueOf(scope.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_PIECE_SCOPE);
        }
    }

    /**
     * 조각에 원본 리뷰의 작성 시각과 장소를 붙여 내려준다.
     *
     * <p>리뷰·장소는 연관관계 없이 id만 들고 있어 N+1을 피하려 일괄 조회한다.
     * 리뷰가 ARCHIVED여도 조각은 그대로 보인다. 정렬 기준은 응답에 실리는 created_at,
     * 즉 리뷰 작성 시각이다 (조각을 만든 순서가 아니다). 고정 조각도 따로 위로 올리지 않는다 —
     * 보드에서 자리를 정하는 값은 position_x/y지 목록 순서가 아니다.
     */
    private PieceListResponse toListResponse(List<Piece> pieces, boolean includeVisibility,
                                             PieceScope scope) {
        if (pieces.isEmpty()) {
            return new PieceListResponse(0, List.of());
        }

        Map<Long, Review> reviewMap = reviewRepository.findAllById(
                        pieces.stream().map(Piece::getReviewId).distinct().toList()).stream()
                .collect(Collectors.toMap(Review::getId, Function.identity()));

        LocalDateTime boardSince = LocalDateTime.now(clock).minusHours(BOARD_RECENT_HOURS);
        List<Piece> visible = pieces.stream()
                // 리뷰를 지우면 조각도 함께 지우므로 정상적으로는 비지 않는다.
                // 그래도 남은 고아 조각은 시각·장소를 채울 수 없어 목록에서 뺀다
                .filter(p -> reviewMap.containsKey(p.getReviewId()))
                .filter(p -> scope != PieceScope.BOARD
                        || isOnBoard(p, reviewMap.get(p.getReviewId()), boardSince))
                .toList();

        if (visible.isEmpty()) {
            return new PieceListResponse(0, List.of());
        }

        Map<Long, Short> pinOrders = renumberPins(visible);

        List<Long> placeIds = visible.stream()
                .map(p -> reviewMap.get(p.getReviewId()).getPlaceId()).distinct().toList();
        Map<Long, Place> placeMap = placeRepository.findAllById(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity()));

        List<PieceListResponse.PieceItem> items = visible.stream()
                .map(p -> {
                    Review review = reviewMap.get(p.getReviewId());
                    Place place = placeMap.get(review.getPlaceId());
                    PieceListResponse.PlaceInfo placeInfo = place != null
                            ? new PieceListResponse.PlaceInfo(place.getId(), place.getName())
                            : new PieceListResponse.PlaceInfo(review.getPlaceId(), null);
                    Short pinOrder = pinOrders.get(p.getId());

                    return new PieceListResponse.PieceItem(
                            p.getId(), p.getReviewId(), p.getImageUrl(), p.getName(),
                            includeVisibility ? p.getVisibility() : null,
                            p.getPositionX(), p.getPositionY(), p.getRotation(),
                            // scale 도입 이전 조각은 컬럼 default로 채워지지만, 값이 비면 기본값으로 내린다
                            p.getScale() != null ? p.getScale() : Piece.DEFAULT_SCALE,
                            pinOrder != null, pinOrder,
                            placeInfo, review.getCreatedAt());
                })
                .sorted(Comparator.comparing(PieceListResponse.PieceItem::createdAt).reversed()
                        .thenComparing(Comparator.comparing(PieceListResponse.PieceItem::id).reversed()))
                .toList();

        return new PieceListResponse(items.size(), items);
    }

    // 고정했거나, 원본 리뷰가 72h 이내면 보드에 붙어 있다. 경계값(정확히 72h 전)은 포함한다
    private boolean isOnBoard(Piece piece, Review review, LocalDateTime boardSince) {
        return piece.isPinned() || !review.getCreatedAt().isBefore(boardSince);
    }

    /**
     * 응답에 실린 고정 조각끼리 저장값 오름차순으로 1부터 다시 매긴다.
     *
     * <p>고정 조각을 지우거나 남의 보드에서 PRIVATE 조각이 빠지면 저장값에 번호가 빈다
     * (1, 3처럼). 그 구멍을 클라이언트가 떠안지 않도록 응답에서는 늘 1부터 이어지게 만든다.
     */
    private Map<Long, Short> renumberPins(List<Piece> pieces) {
        Map<Long, Short> pinOrders = new HashMap<>();
        short order = 1;
        for (Piece piece : pieces.stream()
                .filter(Piece::isPinned)
                .sorted(Comparator.comparing(Piece::getPinOrder))
                .toList()) {
            pinOrders.put(piece.getId(), order++);
        }
        return pinOrders;
    }
}
