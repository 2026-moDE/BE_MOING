package com.moing.backend.domain.magazine.entity;

import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.review.entity.CongestionLevel;

import java.util.List;

/**
 * 매거진 묶음 규칙.
 *
 * <p>헤드라인·우선순위·노출 조건을 한곳에 모아 둔다. 배치는 이 목록을 돌며 묶음을 만들고,
 * 조회는 {@code magazines.rule_key}로 이 값을 되찾아 노출 조건을 판단한다.
 *
 * <p>DB에는 이름이 문자열로 들어간다({@code varchar(50)}). 배치가 매일 전체를 지우고 다시
 * 만들므로, 규칙을 지우거나 이름을 바꿔도 다음 배치에서 정리된다.
 */
public enum MagazineRule {

    /** 24시간 이내 작성 */
    TODAY("오늘 올라온 것들", 50, Display.ALWAYS),

    /** 카페 + 혼잡도 여유 */
    RELAXED_CAFE("지금 한산한 카페", 40, Display.ALWAYS),

    /** 실내 카테고리 — 비 올 때만 노출 */
    RAINY_INDOOR("비 오는 날 들어가 있기 좋은 곳", 30, Display.RAINING),

    /** 22시 이후(KST) 작성 — 저녁 이후에만 노출 */
    LATE_NIGHT("밤에도 사람 있는 곳", 20, Display.EVENING),

    /** 혼잡도 붐빔 */
    CROWDED("줄 서는 곳", 10, Display.ALWAYS);

    /**
     * 실내로 보는 카테고리.
     *
     * <p>{@link PlaceCategory#ETC}는 무엇인지 알 수 없어 넣지 않는다 — 비 오는 날 추천에
     * 야외 장소가 섞이는 쪽이 몇 곳 놓치는 쪽보다 나쁘다.
     */
    public static final List<PlaceCategory> INDOOR_CATEGORIES = List.of(
            PlaceCategory.CAFE,
            PlaceCategory.RESTAURANT,
            PlaceCategory.FOOD,
            PlaceCategory.POPUP,
            PlaceCategory.PERFORMANCE);

    /** 혼잡도를 가리지 않는 규칙이 쓴다 (혼잡도가 없는 리뷰는 어느 묶음에도 들어가지 않는다) */
    public static final List<CongestionLevel> ANY_LEVEL = List.of(CongestionLevel.values());

    private final String headline;
    private final int priority;
    private final Display display;

    MagazineRule(String headline, int priority, Display display) {
        this.headline = headline;
        this.priority = priority;
        this.display = display;
    }

    public String getHeadline() {
        return headline;
    }

    public int getPriority() {
        return priority;
    }

    public Display getDisplay() {
        return display;
    }

    /** 조회 시점에 묶음을 보여줄지 가르는 조건 */
    public enum Display {
        /** 언제나 노출 */
        ALWAYS,
        /** 기준 좌표에 비나 눈이 올 때만 노출 */
        RAINING,
        /** 현재 시각(KST)이 저녁 이후일 때만 노출 */
        EVENING
    }
}
