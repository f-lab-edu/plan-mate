package com.planmate.place.dto;

public record PlaceTextSearchRequest(
        String textQuery,
        String languageCode,
        int pageSize,
        ResolvedDestination destination,
        String pageToken
) {
}
