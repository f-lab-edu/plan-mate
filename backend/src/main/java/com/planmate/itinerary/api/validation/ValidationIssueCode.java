package com.planmate.itinerary.api.validation;

public enum ValidationIssueCode {

    GENERATION_ID_MISMATCH(
            "generationId must match the current itinerary generation."
    ),
    DRAFT_REQUIRED(
            "draft is required."
    ),
    DAYS_REQUIRED(
            "days are required."
    ),
    DAY_COUNT_MISMATCH(
            "days size must match the trip day count."
    ),
    DAY_OUT_OF_RANGE(
            "day must be within the trip day range."
    ),
    DUPLICATE_DAY(
            "day must not be duplicated."
    ),
    DAY_ITEMS_REQUIRED(
            "day items are required."
    ),
    ITEM_REQUIRED(
            "item is required."
    ),
    INVALID_SEQUENCE(
            "sequence must be positive."
    ),
    DUPLICATE_SEQUENCE(
            "sequence must not be duplicated within a day."
    ),
    PLACE_ID_REQUIRED(
            "placeId is required."
    ),
    INVALID_START_TIME(
            "startTime must use HH:mm format."
    ),
    INVALID_DURATION(
            "durationMinutes must be positive."
    ),
    CANDIDATE_NOT_ALLOWED(
            "placeId must be included in the generation candidate whitelist."
    ),
    REQUIRED_PLACE_MISSING(
            "A required place must be included in the itinerary."
    ),
    ITEM_TIME_OVERLAP(
            "Itinerary items must not overlap within the same day."
    ),
    OUTSIDE_DAILY_WINDOW(
            "Itinerary item must stay within the configured daily time window."
    ),
    ITEM_CROSSES_DAY_BOUNDARY(
            "Itinerary item must not continue past the end of the day."
    );

    private final String defaultMessage;

    ValidationIssueCode(String defaultMessage) {
        this.defaultMessage = defaultMessage;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
