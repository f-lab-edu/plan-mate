package com.planmate.itinerary.dto;

import java.util.List;

public record ItineraryResponse(
        String sourceType,
        String sourceSampleId,
        String summary,
        BudgetSummaryResponse budgetSummary,
        List<ItineraryDayResponse> days,
        List<ItineraryAlternativeSuggestionResponse> alternativeSuggestions,
        List<String> verificationWarnings
) {
}
