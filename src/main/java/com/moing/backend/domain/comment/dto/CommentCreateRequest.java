package com.moing.backend.domain.comment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentCreateRequest(
        @NotBlank @Size(max = 200) String content,
        // 미지정 시 false
        @JsonProperty("is_secret") Boolean isSecret
) {}
