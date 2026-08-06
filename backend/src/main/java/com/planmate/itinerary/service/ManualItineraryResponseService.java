package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualItineraryResponseService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final TripAccessChecker tripAccessChecker;
    private final GenerationInputSnapshotStore generationInputSnapshotStore;
    private final GenerationCandidateSnapshotStore generationCandidateSnapshotStore;
    private final AiItineraryDraftValidator aiItineraryDraftValidator;
    private final ItineraryGenerationRepository generationRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ManualItineraryResponseService(
            TripAccessChecker tripAccessChecker,
            GenerationInputSnapshotStore generationInputSnapshotStore,
            GenerationCandidateSnapshotStore generationCandidateSnapshotStore,
            AiItineraryDraftValidator aiItineraryDraftValidator,
            ItineraryGenerationRepository generationRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripAccessChecker = tripAccessChecker;
        this.generationInputSnapshotStore = generationInputSnapshotStore;
        this.generationCandidateSnapshotStore = generationCandidateSnapshotStore;
        this.aiItineraryDraftValidator = aiItineraryDraftValidator;
        this.generationRepository = generationRepository;
        this.itineraryRepository = itineraryRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void submit(Long userId, Long tripId, Long generationId, AiItineraryDraft draft) {
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

        GenerationInputSnapshot snapshot = generationInputSnapshotStore.getRequired(generationId);
        List<GenerationCandidateSnapshot> candidates = generationCandidateSnapshotStore.findAllByGenerationId(generationId);
        aiItineraryDraftValidator.validate(generation.getPromptVersion(), snapshot, candidates, draft);

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
        long candidateCount = generationCandidateSnapshotStore.countByGenerationId(generationId);
        eventPublisher.publishEvent(new ItineraryGenerationStatusChangedEvent(
                tripId,
                generation.getId(),
                previousStatus,
                generation.getStatus(),
                candidateCount,
                generation.getFailureReason(),
                generation.getUpdatedAt()
        ));
    }

    private LocalTime parseTime(String value) {
        return LocalTime.parse(value, TIME_FORMATTER);
    }

    private String normalizePlaceId(String value) {
        return value == null ? "" : value.trim();
    }

    private ItineraryException invalid(String message) {
        return new ItineraryException(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED, message);
    }
}
