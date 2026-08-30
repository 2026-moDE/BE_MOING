package com.moing.backend.domain.place.entity;

public enum PlaceCategory {
    RESTAURANT("음식점"),
    FOOD("음식점"),
    CAFE("카페"),
    POPUP("팝업스토어"),
    PERFORMANCE("공연장"),
    ETC("기타");

    private final String searchQuery;

    PlaceCategory(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    // 네이버 카테고리 텍스트(예: "음식점 > 한식")를 분석해 Enum 반환
    public static PlaceCategory fromNaverCategory(String naverCategory) {
        if (naverCategory == null) return ETC;

        if (naverCategory.contains("카페") || naverCategory.contains("커피")) return CAFE;
        if (naverCategory.contains("음식점") || naverCategory.contains("식당")
                || naverCategory.contains("한식") || naverCategory.contains("중식")
                || naverCategory.contains("일식") || naverCategory.contains("양식")
                || naverCategory.contains("분식") || naverCategory.contains("치킨")
                || naverCategory.contains("피자") || naverCategory.contains("패스트푸드")) return RESTAURANT;
        if (naverCategory.contains("공연") || naverCategory.contains("전시") || naverCategory.contains("미술관")) return PERFORMANCE;
        if (naverCategory.contains("팝업")) return POPUP;

        return ETC;
    }

    // 카카오 category_group_code를 분석해 Enum 반환
    public static PlaceCategory fromKakaoCategoryCode(String code) {
        if (code == null) return ETC;

        return switch (code) {
            case "FD6" -> RESTAURANT;
            case "CE7" -> CAFE;
            case "CT1" -> PERFORMANCE;
            default -> ETC;
        };
    }
}