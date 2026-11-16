package com.moing.backend.domain.admin.controller;

import com.moing.backend.domain.admin.service.AdminAnalyticsService;
import com.moing.backend.global.config.JwtTokenProvider;
import com.moing.backend.global.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 지표 API 접근 제어 테스트.
 *
 * <p>지표에는 사용자 수·기록량이 그대로 담기므로 관리자만 열 수 있어야 한다. 경로가
 * {@code /api/admin/analytics}라 {@code SecurityConfig}의 {@code /api/admin/**} 규칙에
 * 자동으로 걸리는데, 그 "자동"이 실제로 걸리는지 확인한다 — 새 컨트롤러를 다른 경로에
 * 두면 인증 없이 열리는 사고가 난다.
 *
 * <p>서비스는 대역으로 바꿔 둔다. 여기서 보는 것은 집계 결과가 아니라 응답 코드다.
 */
@WebMvcTest(AdminAnalyticsController.class)
@Import({SecurityConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-admin-analytics-auth-test-0123456789",
        "jwt.access-token-expiration=3600000",
        "jwt.refresh-token-expiration=1209600000"
})
class AdminAnalyticsAuthTest {

    private static final String[] ENDPOINTS = {
            "/api/admin/analytics/participation",
            "/api/admin/analytics/freshness",
            "/api/admin/analytics/trust",
            "/api/admin/analytics/coverage",
            "/api/admin/analytics/patterns"
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AdminAnalyticsService adminAnalyticsService;

    /**
     * {@code @EnableJpaAuditing}이 메인 설정에 걸려 있어 JPA 메타모델을 찾는데, 웹 계층만
     * 띄우는 이 테스트에는 엔티티 매니저가 없다. 매핑 컨텍스트를 대역으로 채워 기동시킨다.
     */
    @MockitoBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/admin/analytics/participation",
            "/api/admin/analytics/freshness",
            "/api/admin/analytics/trust",
            "/api/admin/analytics/coverage",
            "/api/admin/analytics/patterns"
    })
    @DisplayName("토큰이 없으면 401")
    void 토큰_없으면_401(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/api/admin/analytics/participation",
            "/api/admin/analytics/freshness",
            "/api/admin/analytics/trust",
            "/api/admin/analytics/coverage",
            "/api/admin/analytics/patterns"
    })
    @DisplayName("일반 사용자 토큰이면 403")
    void 일반_사용자_토큰이면_403(String path) throws Exception {
        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(userToken())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자 토큰이면 통과한다")
    void 관리자_토큰이면_200() throws Exception {
        for (String path : ENDPOINTS) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                    .andExpect(status().isOk());
        }
    }

    private String userToken() {
        return jwtTokenProvider.generateAccessToken(1L);
    }

    private String adminToken() {
        return jwtTokenProvider.generateAccessToken(1L, "ADMIN");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
