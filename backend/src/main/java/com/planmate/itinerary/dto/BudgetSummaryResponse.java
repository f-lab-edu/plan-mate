package com.planmate.itinerary.dto;

import java.util.List;

public record BudgetSummaryResponse(
        String currency,
        int totalMin,
        int totalMax,
        int perPersonMin,
        int perPersonMax,
        List<String> notes
) {
}
