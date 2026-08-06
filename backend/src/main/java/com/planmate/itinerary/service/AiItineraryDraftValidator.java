package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiItineraryDraftValidator {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public void validate(
            String promptVersion,
            GenerationInputSnapshot inputSnapshot,
            List<GenerationCandidateSnapshot> candidates,
            AiItineraryDraft draft
    ) {
        Set<String> includedPlaceIds = validateCommon(inputSnapshot, draft);
        switch (promptVersion) {
            case ItineraryPromptService.VERSION_V1 -> validateV1(inputSnapshot.mustVisitPlaces(), includedPlaceIds);
            case ItineraryPromptService.VERSION_V2 -> validateV2(candidates, includedPlaceIds);
            default -> throw new ItineraryException(ItineraryErrorCode.UNSUPPORTED_PROMPT_VERSION);
        }
    }

    private Set<String> validateCommon(
            GenerationInputSnapshot inputSnapshot,
            AiItineraryDraft draft
    ) {
        if (draft == null) {
            throw invalid("draft is required.");
        }
        if (draft.days() == null || draft.days().isEmpty()) {
            throw invalid("days are required.");
        }
        int tripDayCount = inputSnapshot.tripDayCount();
        if (draft.days().size() != tripDayCount) {
            throw invalid("days size must match trip day count.");
        }

        Set<Integer> days = new HashSet<>();
        Set<String> includedPlaceIds = new HashSet<>();
        for (ItineraryDraftDay day : draft.days()) {
            validateDay(tripDayCount, day, days, includedPlaceIds);
        }
        return includedPlaceIds;
    }

    private void validateDay(
            int tripDayCount,
            ItineraryDraftDay day,
            Set<Integer> days,
            Set<String> includedPlaceIds
    ) {
        if (day == null) {
            throw invalid("day is required.");
        }
        if (day.day() < 1 || day.day() > tripDayCount || !days.add(day.day())) {
            throw invalid("day is duplicated or out of range.");
        }
        if (day.items() == null || day.items().isEmpty()) {
            throw invalid("day items are required.");
        }

        Set<Integer> sequences = new HashSet<>();
        for (ItineraryDraftItem item : day.items()) {
            validateItem(item, sequences, includedPlaceIds);
        }
    }

    private void validateItem(
            ItineraryDraftItem item,
            Set<Integer> sequences,
            Set<String> includedPlaceIds
    ) {
        if (item == null) {
            throw invalid("item is required.");
        }
        if (item.sequence() < 1 || !sequences.add(item.sequence())) {
            throw invalid("sequence is duplicated or out of range.");
        }
        String placeId = normalizePlaceId(item.placeId());
        if (!StringUtils.hasText(placeId)) {
            throw invalid("placeId is required.");
        }
        includedPlaceIds.add(placeId);
        parseTime(item.startTime());
        if (item.durationMinutes() <= 0) {
            throw invalid("durationMinutes must be positive.");
        }
    }

    private void validateV1(
            List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces,
            Set<String> includedPlaceIds
    ) {
        for (GenerationInputSnapshot.MustVisitPlace mustVisitPlace : mustVisitPlaces) {
            String requiredPlaceId = normalizePlaceId(mustVisitPlace.placeId());
            if (StringUtils.hasText(requiredPlaceId) && !includedPlaceIds.contains(requiredPlaceId)) {
                throw invalid("mustVisitPlaceIds must be included in the itinerary.");
            }
        }
    }

    private void validateV2(
            List<GenerationCandidateSnapshot> candidates,
            Set<String> includedPlaceIds
    ) {
        List<GenerationCandidateSnapshot> safeCandidates = candidates == null
                ? List.of()
                : List.copyOf(candidates);
        if (safeCandidates.isEmpty()) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_CANDIDATES_NOT_FOUND);
        }

        Set<String> allowedPlaceIds = new HashSet<>();
        Set<String> requiredPlaceIds = new HashSet<>();
        for (GenerationCandidateSnapshot candidate : safeCandidates) {
            String placeId = normalizePlaceId(candidate.placeId());
            if (StringUtils.hasText(placeId)) {
                allowedPlaceIds.add(placeId);
                if (candidate.forcedMustVisit()) {
                    requiredPlaceIds.add(placeId);
                }
            }
        }

        for (String includedPlaceId : includedPlaceIds) {
            if (!allowedPlaceIds.contains(includedPlaceId)) {
                throw invalid("placeId must be included in candidate whitelist.");
            }
        }
        if (!includedPlaceIds.containsAll(requiredPlaceIds)) {
            throw invalid("forcedMustVisit candidates must be included in the itinerary.");
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw invalid("startTime must use HH:mm format.");
        }
    }

    private String normalizePlaceId(String value) {
        return value == null ? "" : value.trim();
    }

    private ItineraryException invalid(String message) {
        return new ItineraryException(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED, message);
    }
}
