package com.moing.backend.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

/**
 * 약관 동의 요청 DTO
 * 서비스 이용약관 동의 여부와 위치정보 이용약관 동의 여부를 담는다.
 */
@Getter
public class TermsRequest {

    @NotNull
    @JsonProperty("terms_agreed")
    private Boolean termsAgreed;

    @NotNull
    @JsonProperty("location_terms_agreed")
    private Boolean locationTermsAgreed;

    @JsonProperty("marketing_agreed")
    private Boolean marketingAgreed;
}
