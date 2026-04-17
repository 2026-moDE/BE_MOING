package com.moing.backend.domain.place.entity;

public enum PlaceCategory {
    FOOD("음식점"),
    CAFE("카페"),
    POPUP("팝업스토어"),
    PERFORMANCE("공연장"); // '공연'보다 장소 데이터가 더 잘 나오는 '공연장'으로 매핑

    private final String searchQuery;

    PlaceCategory(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public String getSearchQuery() {
        return searchQuery;
    }
}