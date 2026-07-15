package com.moing.backend.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OnboardingRequest(
        String nickname,
        @JsonProperty("terms_agreed")
        Boolean termsAgreed,

        @JsonProperty("location_terms_agreed")
        Boolean locationTermsAgreed,

        @JsonProperty("privacy_agreed")
        Boolean privacyAgreed,

        @JsonProperty("marketing_agreed")
        Boolean marketingAgreed,

        @JsonProperty("profile_image_url")
        String profileImageUrl
) {}
