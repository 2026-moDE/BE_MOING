package com.moing.backend.domain.magazine.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.moing.backend.domain.magazine.entity.MagazineRule;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 매거진 묶음 생성 결과 (관리자용).
 *
 * <p>만든 개수만 내려주면 "왜 이 묶음이 안 보이는지"를 알 수 없다. 조건에 맞는 리뷰가
 * 2개가 안 돼서 건너뛴 규칙을 후보 수와 함께 담아, 데이터가 부족한 것인지 조건이 잘못된
 * 것인지 호출만으로 가려낼 수 있게 한다.
 */
public record MagazineGenerateResponse(
        @JsonProperty("created_count") int createdCount,

        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        @JsonProperty("generated_at") LocalDateTime generatedAt,

        List<Created> magazines,

        /** 후보가 2개 미만이라 만들지 않은 규칙 */
        List<Skipped> skipped
) {
    public record Created(
            Long id,
            @JsonProperty("rule_key") MagazineRule ruleKey,
            String title,
            @JsonProperty("item_count") int itemCount
    ) {}

    public record Skipped(
            @JsonProperty("rule_key") MagazineRule ruleKey,
            /** 장소 중복까지 걷어낸 뒤 남은 후보 수 (0이면 조건에 맞는 리뷰가 아예 없다) */
            @JsonProperty("candidate_count") int candidateCount
    ) {}
}
