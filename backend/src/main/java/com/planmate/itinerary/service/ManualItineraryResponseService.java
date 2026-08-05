package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.GroundedItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.entity.ItineraryItemCreatedSource;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.realtime.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripPlanningSnapshot;
import com.planmate.trip.api.TripPlanningSnapshotReader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ManualItineraryResponseService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TripAccessChecker tripAccessChecker;
    private final TripPlanningSnapshotReader tripPlanningSnapshotReader;
    private final ItineraryGenerationRepository generationRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ManualItineraryResponseService(
            TripAccessChecker tripAccessChecker,
            TripPlanningSnapshotReader tripPlanningSnapshotReader,
            ItineraryGenerationRepository generationRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.tripPlanningSnapshotReader = tripPlanningSnapshotReader;
        this.generationRepository = generationRepository;
        this.itineraryRepository = itineraryRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void submit(Long userId, Long tripId, Long generationId, GroundedItineraryDraft draft) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTripId().equals(tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        if (generation.getStatus() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }
        if (draft == null || !generation.getId().toString().equals(draft.generationId())) {
            throw invalid("generationId가 현재 생성 작업과 일치하지 않습니다.");
        }

        TripPlanningSnapshot snapshot = findPlanningSnapshot(tripId);
        validateDraft(snapshot, draft);

        Instant now = Instant.now(clock);
        generation.markValidating(now);
        ItineraryEntity itinerary = itineraryRepository.save(ItineraryEntity.create(generation, now));
        for (ItineraryDraftDay responseDay : draft.days()) {
            ItineraryDayEntity day = itineraryDayRepository.save(ItineraryDayEntity.create(
                    itinerary,
                    responseDay.day(),
                    snapshot.startDate().plusDays(responseDay.day() - 1L)
            ));
            for (ItineraryDraftItem responseItem : responseDay.items()) {
                itineraryItemRepository.save(ItineraryItemEntity.create(
                        day,
                        responseItem.sequence(),
                        normalizePlaceId(responseItem.placeId()),
                        parseTime(responseItem.startTime()),
                        responseItem.durationMinutes(),
                        ItineraryItemCreatedSource.AI_DRAFT
                ));
            }
        }
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markCompleted(now);
        eventPublisher.publishEvent(new ItineraryGenerationStatusChangedEvent(
                tripId,
                generation.getId(),
                previousStatus,
                generation.getStatus(),
                0,
                generation.getFailureReason(),
                generation.getUpdatedAt()
        ));
    }

    private void validateDraft(
            TripPlanningSnapshot snapshot,
            GroundedItineraryDraft draft
    ) {
        if (draft.days() == null || draft.days().isEmpty()) {
            throw invalid("days는 필수입니다.");
        }
        int tripDayCount = snapshot.tripDayCount();
        if (draft.days().size() != tripDayCount) {
            throw invalid("days 개수는 여행 일수와 일치해야 합니다.");
        }

        Set<Integer> days = new HashSet<>();
        Set<String> includedPlaceIds = new HashSet<>();
        for (ItineraryDraftDay day : draft.days()) {
            validateDay(tripDayCount, day, days, includedPlaceIds);
        }
        validateMustVisitPlaces(snapshot.mustVisitPlaces(), includedPlaceIds);
    }

    private void validateDay(
            int tripDayCount,
            ItineraryDraftDay day,
            Set<Integer> days,
            Set<String> includedPlaceIds
    ) {
        if (day.day() < 1 || day.day() > tripDayCount || !days.add(day.day())) {
            throw invalid("day가 중복되었거나 유효하지 않습니다.");
        }
        if (day.items() == null || day.items().isEmpty()) {
            throw invalid("day items는 필수입니다.");
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
        if (item.sequence() < 1 || !sequences.add(item.sequence())) {
            throw invalid("sequence가 중복되었거나 유효하지 않습니다.");
        }
        String placeId = normalizePlaceId(item.placeId());
        if (!StringUtils.hasText(placeId)) {
            throw invalid("placeId는 필수입니다.");
        }
        includedPlaceIds.add(placeId);
        parseTime(item.startTime());
        if (item.durationMinutes() <= 0) {
            throw invalid("durationMinutes는 양수여야 합니다.");
        }
    }

    private void validateMustVisitPlaces(
            List<TripPlanningSnapshot.MustVisitPlace> mustVisitPlaces,
            Set<String> includedPlaceIds
    ) {
        for (TripPlanningSnapshot.MustVisitPlace mustVisitPlace : mustVisitPlaces) {
            if (StringUtils.hasText(mustVisitPlace.placeId()) && !includedPlaceIds.contains(mustVisitPlace.placeId())) {
                throw invalid("mustVisitPlaceIds는 일정에 포함되어야 합니다.");
            }
        }
    }

    private LocalTime parseTime(String value) {
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw invalid("startTime은 HH:mm 형식이어야 합니다.");
        }
    }

    private String normalizePlaceId(String value) {
        return value == null ? "" : value.trim();
    }

    private TripPlanningSnapshot findPlanningSnapshot(Long tripId) {
        return tripPlanningSnapshotReader.findByTripId(tripId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
    }

    private ItineraryException invalid(String message) {
        return new ItineraryException(ItineraryErrorCode.INVALID_AI_RESPONSE, message);
    }
}
