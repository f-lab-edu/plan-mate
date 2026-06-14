package com.planmate.itinerary.dto;

import java.time.LocalDate;
import java.util.List;

public record ItineraryDayResponse(
        int day,
        LocalDate dateLabel,
        String theme,
        List<ItineraryItemResponse> items
) {
}
