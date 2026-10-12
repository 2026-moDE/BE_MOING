package com.moing.backend.domain.magazine.service;

import com.moing.backend.domain.magazine.dto.MagazineGenerateResponse;
import com.moing.backend.domain.magazine.entity.Magazine;
import com.moing.backend.domain.magazine.entity.MagazineItem;
import com.moing.backend.domain.magazine.entity.MagazineRule;
import com.moing.backend.domain.magazine.repository.MagazineItemRepository;
import com.moing.backend.domain.magazine.repository.MagazineRepository;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;
import com.moing.backend.domain.review.entity.Review;
import com.moing.backend.domain.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 매거진 묶음을 하루 1회 만든다.
 *
 * <p>조회 때마다 조건별로 리뷰를 훑으면 느리고, 큐레이션은 실시간이어야 하는 정보도 아니다.
 * 그래서 배치가 만들어 두고 조회는 읽기만 한다.
 *
 * <p>증분 갱신이 아니라 <b>전체를 지우고 다시 만든다</b>. 어제 묶음에 들어간 리뷰가 지워지거나
 * 블라인드될 수 있고, 조건에 맞는 리뷰도 계속 바뀌어서 덧붙이는 방식으로는 상태가 엉킨다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MagazineBatchService {

    /** 한 묶음에 담는 리뷰 수. 2개 미만이면 묶음을 만들지 않는다 */
    private static final int MIN_ITEMS = 2;
    private static final int MAX_ITEMS = 5;

    /**
     * 규칙별로 받아 올 후보 수.
     *
     * <p>장소별로 하나만 남기므로 {@link #MAX_ITEMS}개를 채우려면 넉넉히 받아야 한다.
     * 한 장소에 리뷰가 몰려 있어도 서로 다른 장소 5곳을 찾을 만한 양이다.
     */
    private static final int CANDIDATE_LIMIT = 100;

    /** TODAY 규칙의 기준 */
    private static final int TODAY_HOURS = 24;

    /** LATE_NIGHT 규칙의 기준 — KST 이 시각 이후에 작성된 리뷰 */
    private static final int LATE_NIGHT_FROM_HOUR = 22;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ReviewRepository reviewRepository;
    private final MagazineRepository magazineRepository;
    private final MagazineItemRepository magazineItemRepository;
    private final Clock clock;

    /**
     * 묶음을 전부 다시 만든다. 하루 1회 {@link MagazineBatchScheduler}가 부른다.
     *
     * <p>지우기와 만들기가 한 트랜잭션이라, 중간에 실패하면 어제 묶음이 그대로 남는다.
     * 빈 바텀시트보다 하루 지난 묶음이 낫다. 그래서 스케줄러를 이 클래스 안에 두지 않는다 —
     * 같은 빈 안에서 부르면 프록시를 타지 않아 이 애너테이션이 아무 일도 하지 않는다.
     *
     * @return 규칙별 생성·건너뛴 결과 (관리자가 수동 실행할 때 쓴다)
     */
    @Transactional
    public MagazineGenerateResponse generate() {
        // 아이템을 먼저 지운다. 외래키가 없어 순서를 바꿔도 에러는 안 나지만 고아 행이 남는다
        magazineItemRepository.deleteAllInBatch();
        magazineRepository.deleteAllInBatch();

        List<MagazineGenerateResponse.Created> created = new ArrayList<>();
        List<MagazineGenerateResponse.Skipped> skipped = new ArrayList<>();

        for (MagazineRule rule : MagazineRule.values()) {
            List<Review> picked = pick(rule);

            if (picked.size() < MIN_ITEMS) {
                // 빈 묶음이 뜨는 것보다 아예 없는 게 낫다
                log.info("[Magazine] {} 건너뜀 (후보 {}개)", rule, picked.size());
                skipped.add(new MagazineGenerateResponse.Skipped(rule, picked.size()));
                continue;
            }

            Magazine magazine = magazineRepository.save(Magazine.of(rule));

            List<MagazineItem> items = new ArrayList<>(picked.size());
            for (int i = 0; i < picked.size(); i++) {
                items.add(MagazineItem.of(magazine.getId(), picked.get(i).getId(), i));
            }
            magazineItemRepository.saveAll(items);

            created.add(new MagazineGenerateResponse.Created(
                    magazine.getId(), rule, magazine.getTitle(), items.size()));
            log.info("[Magazine] {} 생성 ({}개)", rule, items.size());
        }

        log.info("[Magazine] 묶음 {}개 생성, {}개 건너뜀", created.size(), skipped.size());
        return new MagazineGenerateResponse(
                created.size(), LocalDateTime.now(clock), created, skipped);
    }

    /** 규칙에 맞는 리뷰를 모아 장소 중복을 걷어내고 최대 {@link #MAX_ITEMS}개로 자른다 */
    private List<Review> pick(MagazineRule rule) {
        return dedupeByPlace(findCandidates(rule));
    }

    private List<Review> findCandidates(MagazineRule rule) {
        Pageable limit = PageRequest.of(0, CANDIDATE_LIMIT);

        return switch (rule) {
            case RELAXED_CAFE -> reviewRepository.findMagazineCandidates(
                    List.of(CongestionLevel.RELAXED), List.of(PlaceCategory.CAFE), null, limit);

            case RAINY_INDOOR -> reviewRepository.findMagazineCandidates(
                    MagazineRule.ANY_LEVEL, MagazineRule.INDOOR_CATEGORIES, null, limit);

            case CROWDED -> reviewRepository.findMagazineCandidates(
                    List.of(CongestionLevel.VERY_CROWDED), allCategories(), null, limit);

            case TODAY -> reviewRepository.findMagazineCandidates(
                    MagazineRule.ANY_LEVEL, allCategories(),
                    LocalDateTime.now(clock).minusHours(TODAY_HOURS), limit);

            case LATE_NIGHT -> reviewRepository.findMagazineCandidatesByCreatedHours(
                    lateNightStoredHours(), limit);
        };
    }

    /**
     * 같은 장소의 리뷰는 하나만 남긴다.
     *
     * <p>같은 카페 사진 3장이 나란히 뜨면 큐레이션으로 보이지 않는다.
     * 입력이 최신순이므로 장소마다 가장 최근 리뷰가 남는다.
     */
    private List<Review> dedupeByPlace(List<Review> candidates) {
        Set<Long> seenPlaces = new HashSet<>();
        List<Review> picked = new ArrayList<>(MAX_ITEMS);

        for (Review review : candidates) {
            if (picked.size() >= MAX_ITEMS) break;
            if (seenPlaces.add(review.getPlaceId())) {
                picked.add(review);
            }
        }
        return picked;
    }

    /**
     * "KST 22시 이후"에 해당하는 <b>저장된 created_at의 시(hour) 값</b>을 구한다.
     *
     * <p>{@code created_at}은 서버 JVM의 벽시계로 쓰인다(@CreatedDate). 그 기준이 KST인지
     * UTC인지에 따라 같은 순간의 시 값이 달라지므로, DB 함수로 시간대를 변환하는 대신
     * JVM 기준과 KST의 차이를 여기서 환산해 넘긴다. 자정을 넘어가며 감싸이는 경우도
     * 시 값 목록으로 다루면 그대로 처리된다.
     *
     * <p>예: JVM이 UTC면 KST 22·23시는 저장값 13·14시가 된다.
     */
    private List<Integer> lateNightStoredHours() {
        ZoneId jvmZone = clock.getZone();
        LocalDateTime today = LocalDateTime.now(clock).toLocalDate().atStartOfDay();

        List<Integer> hours = new ArrayList<>();
        for (int kstHour = LATE_NIGHT_FROM_HOUR; kstHour < 24; kstHour++) {
            ZonedDateTime kst = today.withHour(kstHour).atZone(KST);
            hours.add(kst.withZoneSameInstant(jvmZone).getHour());
        }
        return hours;
    }

    private List<PlaceCategory> allCategories() {
        return List.of(PlaceCategory.values());
    }
}
