package com.planmate.itinerary.dto;

public record TransportResponse(
        String mode,
        int fromPreviousMinutes,
        int estimatedCostMin,
        int estimatedCostMax,
        String notes
) {
}
