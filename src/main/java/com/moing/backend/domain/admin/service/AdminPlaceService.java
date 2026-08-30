package com.moing.backend.domain.admin.service;

import com.moing.backend.domain.admin.dto.AdminPlaceListResponse;
import com.moing.backend.domain.admin.dto.AdminPlaceUpdateRequest;
import com.moing.backend.domain.place.entity.Place;
import com.moing.backend.domain.place.entity.PlaceCategory;
import com.moing.backend.domain.place.repository.PlaceRepository;
import com.moing.backend.global.exception.CustomException;
import com.moing.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPlaceService {

    private final PlaceRepository placeRepository;

    public AdminPlaceListResponse getPlaces(String keyword, Long cursor, Integer limit) {
        if (limit == null) limit = 20;
        limit = Math.min(limit, 100);

        if (keyword != null) {
            keyword = keyword.trim();
            if (keyword.isEmpty()) keyword = null;
        }

        List<Object[]> rows = placeRepository.findAdminPlaces(keyword, cursor, PageRequest.of(0, limit + 1));

        boolean hasNext = rows.size() > limit;
        List<Object[]> page = hasNext ? rows.subList(0, limit) : rows;
        Long nextCursor = hasNext ? ((Number) page.get(page.size() - 1)[0]).longValue() : null;

        List<AdminPlaceListResponse.PlaceItem> items = page.stream().map(row ->
                new AdminPlaceListResponse.PlaceItem(
                        ((Number) row[0]).longValue(),
                        (String) row[1],       // name
                        (String) row[2],       // address
                        (String) row[3],       // category (nullable)
                        ((Number) row[4]).longValue(), // review_count
                        (Boolean) row[5],      // is_active
                        (LocalDateTime) row[6] // created_at
                )
        ).toList();

        return new AdminPlaceListResponse(items, nextCursor);
    }

    @Transactional
    public void updatePlace(Long placeId, AdminPlaceUpdateRequest request) {
        if (request.getActive() == null && request.getCategory() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }

        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_FOUND));

        if (request.getActive() != null) {
            place.updateIsActive(request.getActive());
        }

        if (request.getCategory() != null) {
            String cat = request.getCategory().trim();
            if (cat.isEmpty()) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
            try {
                place.updateCategory(PlaceCategory.valueOf(cat.toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new CustomException(ErrorCode.INVALID_INPUT);
            }
        }
    }
}
