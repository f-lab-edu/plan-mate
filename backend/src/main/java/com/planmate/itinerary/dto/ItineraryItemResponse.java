package com.planmate.itinerary.dto;

public record ItineraryItemResponse(
        String id,
        int order,
        String startTime,
        String endTime,
        String placeName,
        String category,
        String areaHint,
        String description,
        EstimatedCostResponse estimatedCost,
        ParkingResponse parking,
        TransportResponse transport,
        String whyRecommended,
        boolean needsVerification,
        Double lat,
        Double lng,
        boolean mapVisible
) {
}
