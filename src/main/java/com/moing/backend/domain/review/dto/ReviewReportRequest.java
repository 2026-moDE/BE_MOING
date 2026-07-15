package com.moing.backend.domain.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReviewReportRequest(
        @NotBlank String reason,
        @Size(max = 100) String detail
) {}
