package com.moing.backend.domain.review.entity;

/**
 * 혼잡도 4단계.
 * 서울시 실시간 도시데이터의 4단계(여유/보통/약간 붐빔/붐빔)와 1:1 대응하며,
 * 사용자 리뷰 입력·장소 혼잡도 캐시·지역 혼잡도 응답이 모두 이 척도를 공유한다.
 */
public enum CongestionLevel {

    RELAXED("여유", BubbleColor.GREEN),
    MODERATE("보통", BubbleColor.GREEN),
    CROWDED("약간 붐빔", BubbleColor.YELLOW),
    VERY_CROWDED("붐빔", BubbleColor.RED);

    private final String label;
    private final BubbleColor bubbleColor;

    CongestionLevel(String label, BubbleColor bubbleColor) {
        this.label = label;
        this.bubbleColor = bubbleColor;
    }

    public String getLabel() {
        return label;
    }

    public BubbleColor getBubbleColor() {
        return bubbleColor;
    }

    /** 지도 버블용 3색 */
    public enum BubbleColor {
        GREEN, YELLOW, RED
    }
}
