package com.moing.backend.global.infra;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 댓글 묶음을 Gemini에 보내 문제 소지를 판정받는다.
 *
 * <p>댓글을 하나씩 보내지 않고 한 요청에 묶어 보낸다. 무료 티어의 병목은 토큰이 아니라
 * 하루 요청 수라서, 20건을 묶으면 같은 한도로 20배를 처리할 수 있다.
 *
 * <p>실패하면 예외를 던지지 않고 빈 목록을 돌려준다. 검열은 부가 기능이고,
 * 결과를 저장하지 않으면 다음 주기에 자연히 다시 시도된다.
 */
@Slf4j
@Component
public class GeminiModerationClient {

    private static final String PROMPT = """
            너는 장소 리뷰 앱의 댓글을 검토하는 심사자다.
            아래 댓글들을 각각 판정해라.

            문제로 볼 것: 욕설/비속어, 특정 집단을 향한 혐오 표현, 광고/홍보, 스팸,
            개인정보 노출(전화번호·주소·계좌), 성적으로 노골적인 표현.

            문제로 보지 말 것: 단순한 불평이나 부정적 후기, 가게에 대한 솔직한 비판,
            가벼운 은어나 줄임말, 오타.

            아래 JSON 배열 형식으로만 답해라. 다른 말은 쓰지 마라.
            [{"id": 숫자, "verdict": "SAFE" 또는 "SUSPECT", "category": "욕설|혐오|광고|스팸|개인정보|선정성" 또는 null, "reason": "판단 근거 한 문장"}]

            SAFE면 category는 null로 두고, reason은 짧게 남겨라.
            반드시 입력으로 받은 모든 id에 대해 하나씩 답해라.

            댓글 목록:
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public GeminiModerationClient(
            @Qualifier("moderationRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${ai.moderation.base-url}") String baseUrl,
            @Value("${ai.moderation.api-key:}") String apiKey,
            @Value("${ai.moderation.model}") String model) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String getModel() {
        return model;
    }

    /**
     * @param comments 검사할 댓글 (id → 내용)
     * @return 판정 결과. 호출이 실패하면 빈 목록
     */
    public List<Verdict> moderate(Map<Long, String> comments) {
        if (comments.isEmpty()) {
            return List.of();
        }

        try {
            String responseBody = call(buildPrompt(comments));
            return parse(responseBody, comments.keySet());
        } catch (RestClientException e) {
            // 쿼터 초과·타임아웃·네트워크 오류 모두 여기로 온다. 다음 주기에 다시 시도된다
            log.warn("[검열] Gemini 호출 실패 - model={}, 댓글 {}건", model, comments.size(), e);
            return List.of();
        } catch (Exception e) {
            log.warn("[검열] 응답 파싱 실패 - model={}", model, e);
            return List.of();
        }
    }

    private String buildPrompt(Map<Long, String> comments) {
        StringBuilder sb = new StringBuilder(PROMPT);
        comments.forEach((id, content) -> sb.append("- id ").append(id).append(": ").append(content).append('\n'));
        return sb.toString();
    }

    private String call(String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // 키를 쿼리스트링에 붙이면 로그·에러 메시지에 그대로 남을 수 있어 헤더로 보낸다
        headers.set("x-goog-api-key", apiKey);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("response_mime_type", "application/json"));

        String url = baseUrl + "/models/" + model + ":generateContent";
        return restTemplate.postForObject(url, new HttpEntity<>(body, headers), String.class);
    }

    // Gemini 응답에서 모델이 쓴 JSON 텍스트를 꺼내 판정 목록으로 바꾼다
    private List<Verdict> parse(String responseBody, java.util.Set<Long> requestedIds) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");

        if (textNode.isMissingNode()) {
            log.warn("[검열] 응답에 text가 없다 - {}", root.path("promptFeedback"));
            return List.of();
        }

        JsonNode items = objectMapper.readTree(textNode.asText());
        List<Verdict> verdicts = new ArrayList<>();

        for (JsonNode item : items) {
            long id = item.path("id").asLong();
            // 물어보지 않은 id를 지어내는 경우가 있어 걸러낸다
            if (!requestedIds.contains(id)) {
                continue;
            }
            String verdict = CommentModerationVerdict.normalize(item.path("verdict").asText(null));
            if (verdict == null) {
                continue;
            }
            verdicts.add(new Verdict(id, verdict,
                    trim(item.path("category").asText(null), 30),
                    trim(item.path("reason").asText(null), 300)));
        }

        return verdicts;
    }

    private String trim(String value, int max) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record Verdict(Long commentId, String verdict, String category, String reason) {}

    // 모델이 소문자나 다른 표기로 답할 수 있어 정규화한다
    private static final class CommentModerationVerdict {
        static String normalize(String raw) {
            if (raw == null) return null;
            String upper = raw.trim().toUpperCase();
            return switch (upper) {
                case "SAFE", "SUSPECT" -> upper;
                default -> null;
            };
        }
    }

    // 설정만 확인하는 용도 (스케줄러가 시작 전에 검사한다)
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

}
