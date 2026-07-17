package com.moing.backend.domain.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class AdminReviewBlindRequest {

    @NotBlank(message = "status는 필수입니다")
    private String status;
}
