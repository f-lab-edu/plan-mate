package com.planmate.itinerary.dto;

import java.util.List;

public record ItineraryAlternativeSuggestionResponse(
        String target,
        String reason,
        List<String> candidates
) {
}
