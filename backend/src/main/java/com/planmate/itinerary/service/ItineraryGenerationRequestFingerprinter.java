package com.planmate.itinerary.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ItineraryGenerationRequestFingerprinter {

    private final ObjectMapper objectMapper;

    public ItineraryGenerationRequestFingerprinter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String create(
            TripEntity trip,
            TripPlanningProfileEntity profile,
            String promptVersion,
            String schemaVersion
    ) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("promptVersion", promptVersion);
        value.put("schemaVersion", schemaVersion);
        value.put("tripId", trip.getId());
        value.put("destinationPlaceId", trip.getDestinationPlaceId());
        value.put("startDate", trip.getStartDate());
        value.put("endDate", trip.getEndDate());
        value.put("companionCount", profile.getCompanionCount());
        value.put("companionType", profile.getCompanionType());
        value.put("hasChildren", profile.isHasChildren());
        value.put("childCount", profile.getChildCount());
        value.put("childAgeGroup", profile.getChildAgeGroup());
        value.put("hasSeniors", profile.isHasSeniors());
        value.put("seniorCount", profile.getSeniorCount());
        value.put("currencyCode", profile.getCurrencyCode());
        value.put("budgetAmount", profile.getBudgetAmount());
        value.put("budgetLevel", profile.getBudgetLevel());
        value.put("includedBudgetItems", profile.getIncludedBudgetItems());
        value.put("travelPace", profile.getTravelPace());
        value.put("interests", profile.getInterests());
        value.put("primaryTransportMode", profile.getPrimaryTransportMode());
        value.put("secondaryTransportModes", profile.getSecondaryTransportModes());
        value.put("accommodationMode", profile.getAccommodationMode());
        value.put("accommodationArea", profile.getAccommodationArea());
        value.put("accommodationPlaceId", profile.getAccommodationPlaceId());
        value.put("dailyStartTime", profile.getDailyStartTime());
        value.put("dailyEndTime", profile.getDailyEndTime());
        value.put("mustVisitPlaceIds", profile.getMustVisitPlaces().stream()
                .map(place -> place.placeId() == null ? "" : place.placeId())
                .sorted()
                .toList());
        value.put("avoidConditions", profile.getAvoidConditions());
        value.put("freeRequest", profile.getFreeRequest());
        return sha256(writeJson(value));
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("일정 생성 fingerprint 입력을 JSON으로 직렬화할 수 없습니다.", exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
