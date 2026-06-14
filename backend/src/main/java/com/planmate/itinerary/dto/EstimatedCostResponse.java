package com.planmate.itinerary.dto;

import java.util.List;

public record EstimatedCostResponse(
        int min,
        int max,
        List<String> included
) {
}
