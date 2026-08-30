package com.moing.backend.domain.comment.service;

import com.moing.backend.domain.comment.entity.CommentModeration;
import com.moing.backend.domain.comment.repository.CommentModerationRepository;
import com.moing.backend.domain.comment.repository.CommentRepository;
import com.moing.backend.global.infra.GeminiModerationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 5분마다 아직 검사하지 않은 댓글을 모아 AI에 판정을 받고 결과를 저장한다.
 *
 * <p>댓글 작성 경로에는 전혀 관여하지 않는다. 이 스케줄러가 통째로 멈춰도
 * 유저는 아무 영향을 받지 않고, 밀린 댓글은 다음 주기에 다시 집힌다.
 *
 * <p>무료 티어 한도를 넘기지 않도록 하루 호출 수를 자체적으로 제한한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentModerationScheduler {

    private final CommentRepository commentRepository;
    private final CommentModerationRepository moderationRepository;
    private final GeminiModerationClient client;

    @Value("${ai.moderation.enabled}")
    private boolean enabled;

    @Value("${ai.moderation.batch-size}")
    private int batchSize;

    @Value("${ai.moderation.daily-call-limit}")
    private int dailyCallLimit;

    // 하루 호출 수 (서버 재시작 시 초기화된다 - 정확한 회계가 아니라 폭주 방지용이다)
    private LocalDate callDate = LocalDate.now();
    private int callCount = 0;

    // 트랜잭션을 걸지 않는다. AI 호출을 기다리는 20초 동안 DB 커넥션을 붙들고 있게 되기 때문이다.
    // 조회와 저장은 각각 독립적이어도 문제가 없다
    @Scheduled(fixedDelay = 5 * 60 * 1000) // 5분
    public void moderateComments() {
        if (!enabled) {
            return;
        }
        if (!client.isConfigured()) {
            log.warn("[검열] 켜져 있지만 API 키가 없어 건너뛴다");
            return;
        }
        if (!consumeDailyQuota()) {
            log.warn("[검열] 하루 호출 한도({}) 도달, 내일 재개", dailyCallLimit);
            return;
        }

        List<Object[]> rows = commentRepository.findUncheckedComments(PageRequest.of(0, batchSize));
        if (rows.isEmpty()) {
            return;
        }

        Map<Long, String> targets = new LinkedHashMap<>();
        for (Object[] row : rows) {
            targets.put(((Number) row[0]).longValue(), (String) row[1]);
        }

        List<GeminiModerationClient.Verdict> verdicts = client.moderate(targets);
        if (verdicts.isEmpty()) {
            // 호출이 실패했으면 아무것도 저장하지 않는다. 다음 주기에 같은 댓글을 다시 집는다
            return;
        }

        List<CommentModeration> saved = verdicts.stream()
                .map(v -> CommentModeration.builder()
                        .commentId(v.commentId())
                        .verdict(v.verdict())
                        .category(v.category())
                        .reason(v.reason())
                        .model(client.getModel())
                        .build())
                .toList();

        moderationRepository.saveAll(saved);

        long suspectCount = saved.stream().filter(CommentModeration::isSuspect).count();
        log.info("[검열] {}건 검사 완료, 의심 {}건", saved.size(), suspectCount);
    }

    // 날짜가 바뀌면 카운터를 초기화하고, 한도가 남아 있으면 1회 차감한다
    private boolean consumeDailyQuota() {
        LocalDate today = LocalDate.now();
        if (!today.equals(callDate)) {
            callDate = today;
            callCount = 0;
        }
        if (callCount >= dailyCallLimit) {
            return false;
        }
        callCount++;
        return true;
    }
}
