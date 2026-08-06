package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
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
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualItineraryResponseService {

    private final TripAccessChecker tripAccessChecker;
    private final GenerationInputSnapshotStore generationInputSnapshotStore;
    private final GenerationCandidateSnapshotStore generationCandidateSnapshotStore;
    private final AiItineraryDraftValidator aiItineraryDraftValidator;
    private final AiItineraryDraftNormalizer aiItineraryDraftNormalizer;
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
            AiItineraryDraftNormalizer aiItineraryDraftNormalizer,
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
        this.aiItineraryDraftNormalizer = aiItineraryDraftNormalizer;
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
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTripId().equals(tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        validateDraftGenerationId(generation.getId(), draft);

        if (generation.getStatus() == ItineraryGenerationStatus.READY_FOR_PLANNING) {
            handleFirstSubmit(generation, draft);
            return;
        }
        if (generation.getStatus() == ItineraryGenerationStatus.COMPLETED) {
            handleCompletedReplay(generation.getId(), draft);
            return;
        }
        throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
    }

    private void handleFirstSubmit(ItineraryGenerationEntity generation, AiItineraryDraft draft) {
        Long generationId = generation.getId();
        if (itineraryRepository.findByGeneration_Id(generationId).isPresent()) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_ITINERARY_STATE_INCONSISTENT);
        }

        GenerationInputSnapshot snapshot = generationInputSnapshotStore.getRequired(generationId);
        List<GenerationCandidateSnapshot> candidates = generationCandidateSnapshotStore.findAllByGenerationId(generationId);
        aiItineraryDraftValidator.validate(generation.getPromptVersion(), snapshot, candidates, draft);
        NormalizedAiItineraryDraft normalizedDraft = aiItineraryDraftNormalizer.normalize(generationId, draft);

        Instant now = Instant.now(clock);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        saveItinerary(generation, snapshot, normalizedDraft, now);
        generation.markCompleted(now);
        publishCompletedEvent(generation.getTripId(), generation, previousStatus, generationId);
    }

    private void handleCompletedReplay(Long generationId, AiItineraryDraft draft) {
        ItineraryEntity itinerary = itineraryRepository.findByGeneration_Id(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_ITINERARY_STATE_INCONSISTENT));
        NormalizedAiItineraryDraft incomingDraft = aiItineraryDraftNormalizer.normalize(generationId, draft);
        NormalizedAiItineraryDraft persistedDraft = aiItineraryDraftNormalizer.normalize(itinerary);
        if (!incomingDraft.equals(persistedDraft)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_ALREADY_COMPLETED_WITH_DIFFERENT_DRAFT);
        }
    }

    private void saveItinerary(
            ItineraryGenerationEntity generation,
            GenerationInputSnapshot snapshot,
            NormalizedAiItineraryDraft normalizedDraft,
            Instant now
    ) {
        ItineraryEntity itinerary = itineraryRepository.save(ItineraryEntity.create(generation, now));
        for (NormalizedAiItineraryDraft.Day responseDay : normalizedDraft.days()) {
            ItineraryDayEntity day = itineraryDayRepository.save(ItineraryDayEntity.create(
                    itinerary,
                    responseDay.day(),
                    snapshot.startDate().plusDays(responseDay.day() - 1L)
            ));
            for (NormalizedAiItineraryDraft.Item responseItem : responseDay.items()) {
                itineraryItemRepository.save(ItineraryItemEntity.create(
                        day,
                        responseItem.sequence(),
                        responseItem.placeId(),
                        responseItem.startTime(),
                        responseItem.durationMinutes(),
                        ItineraryItemCreatedSource.AI_DRAFT
                ));
            }
        }
    }

    private void publishCompletedEvent(
            Long tripId,
            ItineraryGenerationEntity generation,
            ItineraryGenerationStatus previousStatus,
            Long generationId
    ) {
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

    private void validateDraftGenerationId(Long generationId, AiItineraryDraft draft) {
        if (draft == null || !generationId.toString().equals(draft.generationId())) {
            throw invalid("generationId must match the current itinerary generation.");
        }
    }

    private ItineraryException invalid(String message) {
        return new ItineraryException(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED, message);
    }
}
