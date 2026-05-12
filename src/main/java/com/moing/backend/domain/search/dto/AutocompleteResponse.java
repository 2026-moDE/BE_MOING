package com.moing.backend.domain.search.dto;

import java.util.List;

public record AutocompleteResponse(List<Suggestion> suggestions) {

    public record Suggestion(
            Long id,
            String name,
            String address,
            String category
    ) {}
}
