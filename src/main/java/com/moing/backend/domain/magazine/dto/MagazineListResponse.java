package com.moing.backend.domain.magazine.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 홈 바텀시트 매거진 목록 (가로 스크롤 카드).
 *
 * <p>카드 한 장에 필요한 것만 담는다. 리뷰 한 장씩 넘겨보는 데 쓰는 정보는
 * 상세 조회(GET /api/magazines/{id})가 내려준다.
 */
public record MagazineListResponse(List<MagazineItem> magazines) {

    public record MagazineItem(
            Long id,
            String title,

            /** 첫 번째 아이템 리뷰의 썸네일. 그 리뷰에 썸네일이 없으면 원본으로 폴백한다 */
            @JsonProperty("cover_image_url") String coverImageUrl,

            /**
             * 묶음에 담긴 리뷰 수.
             *
             * <p>배치 이후 지워지거나 블라인드된 리뷰는 빼고 센다. 상세 조회가 같은 기준으로
             * 걸러내므로, 이렇게 해야 카드에 뜬 개수와 열어 본 장수가 어긋나지 않는다.
             */
            @JsonProperty("item_count") int itemCount
    ) {}
}
