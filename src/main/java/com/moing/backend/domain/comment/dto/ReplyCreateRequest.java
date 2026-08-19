package com.moing.backend.domain.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReplyCreateRequest(
        @NotBlank @Size(max = 200) String content
) {}
