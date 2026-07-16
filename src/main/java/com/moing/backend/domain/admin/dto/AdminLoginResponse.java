package com.moing.backend.domain.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.moing.backend.domain.admin.entity.Admin;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonPropertyOrder({"access_token", "admin"})
public class AdminLoginResponse {

    @JsonProperty("access_token")
    private String accessToken;

    private AdminInfo admin;

    @Getter
    @Builder
    @JsonPropertyOrder({"id", "name", "email"})
    public static class AdminInfo {
        private Long id;
        private String name;
        private String email;

        public static AdminInfo from(Admin admin) {
            return AdminInfo.builder()
                    .id(admin.getId())
                    .name(admin.getName())
                    .email(admin.getEmail())
                    .build();
        }
    }
}
