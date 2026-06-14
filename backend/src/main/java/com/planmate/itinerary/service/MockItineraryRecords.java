package com.planmate.itinerary.service;

import java.util.List;

record MockItinerarySample(
        String mockSampleId,
        String tripTitle,
        String destination,
        String summary,
        MockBudgetSummary budgetSummary,
        List<MockItineraryDay> days,
        List<MockAlternativeSuggestion> alternativeSuggestions,
        List<String> verificationWarnings
) {
}

record MockBudgetSummary(
        String currency,
        int totalMin,
        int totalMax,
        int perPersonMin,
        int perPersonMax,
        List<String> notes
) {
}

record MockItineraryDay(
        int day,
        String dateLabel,
        String theme,
        List<MockItineraryItem> items
) {
}

record MockItineraryItem(
        int order,
        String startTime,
        String endTime,
        String placeName,
        String category,
        String areaHint,
        String description,
        MockEstimatedCost estimatedCost,
        MockParking parking,
        MockTransport transport,
        String whyRecommended,
        boolean needsVerification,
        Double lat,
        Double lng,
        Boolean mapVisible
) {
}

record MockEstimatedCost(
        int min,
        int max,
        List<String> included
) {
}

record MockParking(
        boolean required,
        int estimatedCostMin,
        int estimatedCostMax,
        String notes
) {
}

record MockTransport(
        String mode,
        int fromPreviousMinutes,
        int estimatedCostMin,
        int estimatedCostMax,
        String notes
) {
}

record MockAlternativeSuggestion(
        String target,
        String reason,
        List<String> candidates
) {
}
