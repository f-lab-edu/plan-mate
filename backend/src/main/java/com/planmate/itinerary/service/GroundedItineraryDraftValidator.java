package com.planmate.itinerary.service;

import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.itinerary.dto.GroundedItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.generation.ItineraryDraftGenerationException;
import com.planmate.itinerary.generation.ItineraryDraftGenerationFailureCode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.entity.TripEntity;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GroundedItineraryDraftValidator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AiItineraryProperties properties;

    public GroundedItineraryDraftValidator(AiItineraryProperties properties) {
        this.properties = properties;
    }

    public void validate(TripEntity trip, List<MustVisitPlaceSnapshot> mustVisitPlaces, GroundedItineraryDraft draft) {
        if (draft == null) {
            throw invalid("AI 일정 응답이 비어 있습니다.");
        }
        if (draft.days() == null || draft.days().isEmpty()) {
            throw invalid("AI 일정 응답에는 days가 필요합니다.");
        }
        int tripDayCount = tripDayCount(trip);
        if (draft.days().size() != tripDayCount) {
            throw invalid("AI 일정의 days 개수는 여행 일수와 일치해야 합니다.");
        }

        Set<Integer> days = new HashSet<>();
        Set<String> includedPlaceIds = new HashSet<>();
        Map<String, Integer> placeUseCounts = new HashMap<>();
        for (ItineraryDraftDay day : draft.days()) {
            validateDay(tripDayCount, day, days, includedPlaceIds, placeUseCounts);
        }
        validateMustVisitPlaces(mustVisitPlaces, includedPlaceIds);
        validatePlaceRepetition(placeUseCounts);
    }

    private void validateDay(
            int tripDayCount,
            ItineraryDraftDay day,
            Set<Integer> days,
            Set<String> includedPlaceIds,
            Map<String, Integer> placeUseCounts
    ) {
        if (day == null || day.day() < 1 || day.day() > tripDayCount || !days.add(day.day())) {
            throw invalid("AI 일정의 day가 중복되었거나 여행 범위를 벗어났습니다.");
        }
        if (day.items() == null || day.items().isEmpty()) {
            throw invalid("AI 일정의 day items가 필요합니다.");
        }
        if (day.items().size() > properties.getMaxItemsPerDay()) {
            throw invalid("AI 일정의 하루 방문지가 너무 많습니다.");
        }

        Set<Integer> sequences = new HashSet<>();
        for (ItineraryDraftItem item : day.items()) {
            validateItem(item, sequences, includedPlaceIds, placeUseCounts);
        }
    }

    private void validateItem(
            ItineraryDraftItem item,
            Set<Integer> sequences,
            Set<String> includedPlaceIds,
            Map<String, Integer> placeUseCounts
    ) {
        if (item == null || item.sequence() < 1 || !sequences.add(item.sequence())) {
            throw invalid("AI 일정의 sequence가 중복되었거나 올바르지 않습니다.");
        }
        String placeId = normalizePlaceId(item.placeId());
        if (!StringUtils.hasText(placeId)) {
            throw invalid("AI 일정 항목에는 placeId가 필요합니다.");
        }
        parseTime(item.startTime());
        if (item.durationMinutes() <= 0) {
            throw invalid("AI 일정 항목의 durationMinutes는 양수여야 합니다.");
        }
        includedPlaceIds.add(placeId);
        placeUseCounts.merge(placeId, 1, Integer::sum);
    }

    private void validateMustVisitPlaces(List<MustVisitPlaceSnapshot> mustVisitPlaces, Set<String> includedPlaceIds) {
        for (MustVisitPlaceSnapshot mustVisitPlace : mustVisitPlaces) {
            if (StringUtils.hasText(mustVisitPlace.placeId()) && !includedPlaceIds.contains(mustVisitPlace.placeId())) {
                throw invalid("AI 일정에는 필수 방문지가 포함되어야 합니다.");
            }
        }
    }

    private void validatePlaceRepetition(Map<String, Integer> placeUseCounts) {
        for (Map.Entry<String, Integer> entry : placeUseCounts.entrySet()) {
            if (entry.getValue() > properties.getMaxRepeatPerPlace()) {
                throw invalid("AI 일정에서 같은 장소가 너무 많이 반복되었습니다.");
            }
        }
    }

    public LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw invalid("AI 일정 항목의 startTime은 HH:mm 형식이어야 합니다.");
        }
    }

    public String normalizePlaceId(String value) {
        return value == null ? "" : value.trim();
    }

    private int tripDayCount(TripEntity trip) {
        return Math.toIntExact(ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1);
    }

    private ItineraryDraftGenerationException invalid(String message) {
        return new ItineraryDraftGenerationException(
                ItineraryDraftGenerationFailureCode.AI_RESPONSE_VALIDATION_FAILED,
                message,
                false
        );
    }
}
