package com.planmate.itinerary.service;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.realtime.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripPlanningSnapshot;
import com.planmate.trip.api.TripPlanningSnapshotReader;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItineraryGenerationPersistenceService {

    private static final String ITINERARY_GENERATION_AGGREGATE_TYPE = "ITINERARY_GENERATION";
    private static final String ITINERARY_GENERATION_REQUESTED_EVENT_TYPE = "ITINERARY_GENERATION_REQUESTED";

    private final ItineraryGenerationRepository generationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TripAccessChecker tripAccessChecker;
    private final TripPlanningSnapshotReader tripPlanningSnapshotReader;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ItineraryGenerationPersistenceService(
            ItineraryGenerationRepository generationRepository,
            OutboxEventRepository outboxEventRepository,
            TripAccessChecker tripAccessChecker,
            TripPlanningSnapshotReader tripPlanningSnapshotReader,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.generationRepository = generationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tripAccessChecker = tripAccessChecker;
        this.tripPlanningSnapshotReader = tripPlanningSnapshotReader;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ItineraryGenerationEntity createGenerationRequest(Long userId, Long tripId, String promptVersion) {
        tripAccessChecker.checkAccessible(userId, tripId);
        Instant now = Instant.now(clock);
        ItineraryGenerationEntity generation = generationRepository.save(ItineraryGenerationEntity.create(tripId, promptVersion, now));
        outboxEventRepository.save(OutboxEventEntity.create(
                ITINERARY_GENERATION_AGGREGATE_TYPE,
                generation.getId().toString(),
                ITINERARY_GENERATION_REQUESTED_EVENT_TYPE,
                itineraryGenerationRequestedPayload(generation.getId(), tripId, userId),
                now
        ));
        return generation;
    }

    @Transactional
    public void markCollecting(Long generationId) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        generation.markCollecting(Instant.now(clock));
    }

    @Transactional
    public boolean markCollectingIfCreated(Long userId, Long tripId, Long generationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generationBelongsToTrip(generation, tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        if (generation.getStatus() != ItineraryGenerationStatus.CREATED) {
            return false;
        }
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        Instant now = Instant.now(clock);
        generation.markCollecting(now);
        publishStatusChanged(tripId, generation, previousStatus, 0);
        return true;
    }

    @Transactional(readOnly = true)
    public GenerationCollectionContext loadCollectionContext(Long userId, Long tripId, Long generationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generationBelongsToTrip(generation, tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        TripPlanningSnapshot snapshot = findPlanningSnapshot(tripId);
        ensureDestinationResolved(snapshot);
        return new GenerationCollectionContext(generation.getId(), snapshot);
    }

    @Transactional
    public void markReadyForPlanning(Long generationId) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markReady(Instant.now(clock));
        publishStatusChanged(generation.getTripId(), generation, previousStatus, 0);
    }

    @Transactional
    public void markFailed(Long generationId, String safeReason) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markFailed(safeReason, Instant.now(clock));
        publishStatusChanged(
                generation.getTripId(),
                generation,
                previousStatus,
                0
        );
    }

    @Transactional(readOnly = true)
    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generationBelongsToTrip(generation, tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        return new ItineraryGenerationDetailResponse(
                generation.getId().toString(),
                tripId.toString(),
                generation.getStatus(),
                generation.getPromptVersion(),
                0,
                generation.getFailureReason(),
                generation.getCreatedAt(),
                generation.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public Optional<ItineraryGenerationDetailResponse> getLatest(Long userId, Long tripId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        return generationRepository.findFirstByTripIdOrderByCreatedAtDesc(tripId)
                .map(generation -> toDetailResponse(tripId, generation));
    }

    @Transactional(readOnly = true)
    public AiRequestContext loadAiRequestContext(Long userId, Long tripId, Long generationId) {
        tripAccessChecker.checkAccessible(userId, tripId);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generationBelongsToTrip(generation, tripId)) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        TripPlanningSnapshot snapshot = findPlanningSnapshot(tripId);
        return new AiRequestContext(generation, snapshot);
    }

    private ItineraryGenerationEntity findGeneration(Long generationId) {
        return generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
    }

    private boolean generationBelongsToTrip(ItineraryGenerationEntity generation, Long tripId) {
        return generation.getTripId().equals(tripId);
    }

    private TripPlanningSnapshot findPlanningSnapshot(Long tripId) {
        return tripPlanningSnapshotReader.findByTripId(tripId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
    }

    private void ensureDestinationResolved(TripPlanningSnapshot snapshot) {
        if (snapshot.destination().latitude() == null || snapshot.destination().longitude() == null) {
            throw new ItineraryException(ItineraryErrorCode.DESTINATION_NOT_RESOLVED);
        }
    }

    private ItineraryGenerationDetailResponse toDetailResponse(Long tripId, ItineraryGenerationEntity generation) {
        return new ItineraryGenerationDetailResponse(
                generation.getId().toString(),
                tripId.toString(),
                generation.getStatus(),
                generation.getPromptVersion(),
                0,
                generation.getFailureReason(),
                generation.getCreatedAt(),
                generation.getUpdatedAt()
        );
    }

    private void publishStatusChanged(
            Long tripId,
            ItineraryGenerationEntity generation,
            ItineraryGenerationStatus previousStatus,
            long candidateCount
    ) {
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

    private Map<String, Object> itineraryGenerationRequestedPayload(Long generationId, Long tripId, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generationId", generationId);
        payload.put("tripId", tripId);
        payload.put("userId", userId);
        return payload;
    }

    public record GenerationCollectionContext(
            Long generationId,
            TripPlanningSnapshot snapshot
    ) {
    }

    public record AiRequestContext(
            ItineraryGenerationEntity generation,
            TripPlanningSnapshot snapshot
    ) {
    }
}
