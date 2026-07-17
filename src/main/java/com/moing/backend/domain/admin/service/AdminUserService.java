package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminUserListResponse;
import com.moing.backend.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserListResponse getUsers(Long cursor, Integer limit) {
        if (limit == null) limit = 20;
        limit = Math.min(limit, 100);

        List<Object[]> rows = userRepository.findUsersWithReviewCount(cursor, PageRequest.of(0, limit + 1));

        boolean hasNext = rows.size() > limit;
        List<Object[]> page = hasNext ? rows.subList(0, limit) : rows;
        Long nextCursor = hasNext ? (Long) page.get(page.size() - 1)[0] : null;

        List<AdminUserListResponse.UserItem> items = page.stream().map(row ->
                new AdminUserListResponse.UserItem(
                        (Long) row[0],
                        (String) row[1],
                        (String) row[2],
                        (String) row[3],
                        (Long) row[4],
                        (java.time.LocalDateTime) row[5]
                )
        ).toList();

        return new AdminUserListResponse(items, nextCursor);
    }
}
