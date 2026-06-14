package com.planmate.itinerary.dto;

public record ParkingResponse(
        boolean required,
        int estimatedCostMin,
        int estimatedCostMax,
        String notes
) {
}
