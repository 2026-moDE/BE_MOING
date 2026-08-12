package com.moing.backend.domain.reaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactionCreateRequest(
        // 상한은 emoji 컬럼(varchar 32)에 맞춘다.
        // @Size는 자바 char 수를 세고 Postgres는 코드 포인트를 세므로,
        // 32자 이하면 컬럼을 넘칠 수 없다 (char 수 >= 코드 포인트 수).
        @NotBlank(message = "이모지를 입력해주세요")
        @Size(max = 32, message = "이모지가 너무 깁니다") String emoji
) {}
