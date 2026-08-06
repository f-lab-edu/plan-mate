package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AiItineraryDraftNormalizer {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public NormalizedAiItineraryDraft normalize(Long generationId, AiItineraryDraft draft) {
        if (draft == null) {
            throw invalid("draft is required.");
        }
        if (draft.days() == null || draft.days().isEmpty()) {
            throw invalid("days are required.");
        }

        Set<Integer> dayNumbers = new HashSet<>();
        List<NormalizedAiItineraryDraft.Day> days = draft.days().stream()
                .map(day -> normalizeDay(day, dayNumbers))
                .sorted(Comparator.comparingInt(NormalizedAiItineraryDraft.Day::day))
                .toList();
        return new NormalizedAiItineraryDraft(generationId, days);
    }

    public NormalizedAiItineraryDraft normalize(ItineraryEntity itinerary) {
        List<NormalizedAiItineraryDraft.Day> days = itinerary.getDays().stream()
                .map(this::normalizeDay)
                .sorted(Comparator.comparingInt(NormalizedAiItineraryDraft.Day::day))
                .toList();
        return new NormalizedAiItineraryDraft(itinerary.getGeneration().getId(), days);
    }

    private NormalizedAiItineraryDraft.Day normalizeDay(ItineraryDraftDay day, Set<Integer> dayNumbers) {
        if (day == null) {
            throw invalid("day is required.");
        }
        if (day.day() < 1 || !dayNumbers.add(day.day())) {
            throw invalid("day is duplicated or out of range.");
        }
        if (day.items() == null || day.items().isEmpty()) {
            throw invalid("day items are required.");
        }

        Set<Integer> sequences = new HashSet<>();
        List<NormalizedAiItineraryDraft.Item> items = day.items().stream()
                .map(item -> normalizeItem(item, sequences))
                .sorted(Comparator.comparingInt(NormalizedAiItineraryDraft.Item::sequence))
                .toList();
        return new NormalizedAiItineraryDraft.Day(day.day(), items);
    }

    private NormalizedAiItineraryDraft.Item normalizeItem(ItineraryDraftItem item, Set<Integer> sequences) {
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
        if (item.durationMinutes() <= 0) {
            throw invalid("durationMinutes must be positive.");
        }
        return new NormalizedAiItineraryDraft.Item(
                item.sequence(),
                placeId,
                parseTime(item.startTime()),
                item.durationMinutes()
        );
    }

    private NormalizedAiItineraryDraft.Day normalizeDay(ItineraryDayEntity day) {
        List<NormalizedAiItineraryDraft.Item> items = day.getItems().stream()
                .map(this::normalizeItem)
                .sorted(Comparator.comparingInt(NormalizedAiItineraryDraft.Item::sequence))
                .toList();
        return new NormalizedAiItineraryDraft.Day(day.getDay(), items);
    }

    private NormalizedAiItineraryDraft.Item normalizeItem(ItineraryItemEntity item) {
        return new NormalizedAiItineraryDraft.Item(
                item.getSequence(),
                normalizePlaceId(item.getPlaceId()),
                item.getStartTime(),
                item.getDurationMinutes()
        );
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
