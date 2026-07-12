package com.planmate.itinerary.service;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.generation.ItineraryGenerationContext;
import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.itinerary.dto.GroundedItineraryDraft;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
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
import com.planmate.place.dto.GeoPoint;
import com.planmate.place.dto.GeoViewport;
import com.planmate.place.dto.ResolvedDestination;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
    private static final List<ItineraryGenerationStatus> REUSABLE_STATUSES = List.of(
            ItineraryGenerationStatus.CREATED,
            ItineraryGenerationStatus.COLLECTING_CANDIDATES,
            ItineraryGenerationStatus.PLANNING,
            ItineraryGenerationStatus.VALIDATING,
            ItineraryGenerationStatus.READY_FOR_PLANNING,
            ItineraryGenerationStatus.COMPLETED
    );

    private final ItineraryGenerationRepository generationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final TripRepository tripRepository;
    private final TripPlanningProfileRepository tripPlanningProfileRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final AiItineraryProperties aiItineraryProperties;
    private final ItineraryGenerationRequestFingerprinter fingerprinter;
    private final GroundedItineraryDraftValidator draftValidator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ItineraryGenerationPersistenceService(
            ItineraryGenerationRepository generationRepository,
            OutboxEventRepository outboxEventRepository,
            TripRepository tripRepository,
            TripPlanningProfileRepository tripPlanningProfileRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            AiItineraryProperties aiItineraryProperties,
            ItineraryGenerationRequestFingerprinter fingerprinter,
            GroundedItineraryDraftValidator draftValidator,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.generationRepository = generationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.tripRepository = tripRepository;
        this.tripPlanningProfileRepository = tripPlanningProfileRepository;
        this.itineraryRepository = itineraryRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.aiItineraryProperties = aiItineraryProperties;
        this.fingerprinter = fingerprinter;
        this.draftValidator = draftValidator;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public ItineraryGenerationEntity createGenerationRequest(
            Long userId,
            Long tripId,
            String promptVersion,
            boolean forceRegenerate
    ) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
        String requestFingerprint = fingerprinter.create(
                trip,
                profile,
                promptVersion,
                aiItineraryProperties.getSchemaVersion()
        );
        if (!forceRegenerate) {
            Optional<ItineraryGenerationEntity> reusableGeneration =
                    generationRepository.findFirstByTrip_IdAndRequestFingerprintAndStatusInOrderByCreatedAtDesc(
                            trip.getId(),
                            requestFingerprint,
                            REUSABLE_STATUSES
                    );
            if (reusableGeneration.isPresent()) {
                return reusableGeneration.get();
            }
        }

        Instant now = Instant.now(clock);
        ItineraryGenerationEntity generation = generationRepository.save(ItineraryGenerationEntity.create(
                trip,
                promptVersion,
                aiItineraryProperties.getProvider(),
                aiItineraryProperties.getModel(),
                aiItineraryProperties.getSchemaVersion(),
                requestFingerprint,
                now
        ));
        outboxEventRepository.save(OutboxEventEntity.create(
                ITINERARY_GENERATION_AGGREGATE_TYPE,
                generation.getId().toString(),
                ITINERARY_GENERATION_REQUESTED_EVENT_TYPE,
                itineraryGenerationRequestedPayload(generation.getId(), trip.getId(), userId),
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
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        if (generation.getStatus() != ItineraryGenerationStatus.CREATED) {
            return false;
        }
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        Instant now = Instant.now(clock);
        generation.markCollecting(now);
        publishStatusChanged(trip.getId(), generation, previousStatus, 0);
        return true;
    }

    @Transactional
    public boolean markPlanningIfCreated(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findWithLockById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        if (generation.getStatus() != ItineraryGenerationStatus.CREATED) {
            return false;
        }
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        Instant now = Instant.now(clock);
        generation.markPlanning(now);
        publishStatusChanged(trip.getId(), generation, previousStatus, 0);
        return true;
    }

    @Transactional(readOnly = true)
    public GenerationCollectionContext loadCollectionContext(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
        return new GenerationCollectionContext(generation.getId(), toResolvedDestination(trip), profile);
    }

    @Transactional(readOnly = true)
    public ItineraryGenerationContext loadGenerationContext(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
        return toGenerationContext(trip, generation, profile);
    }

    @Transactional
    public void markReadyForPlanning(Long generationId) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markReady(Instant.now(clock));
        publishStatusChanged(generation.getTrip().getId(), generation, previousStatus, 0);
    }

    @Transactional
    public void markFailed(Long generationId, String safeReason) {
        markFailed(generationId, safeReason, safeReason);
    }

    @Transactional
    public void markFailed(Long generationId, String failureCode, String userMessage) {
        ItineraryGenerationEntity generation = findGeneration(generationId);
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.markFailed(failureCode, userMessage, Instant.now(clock));
        publishStatusChanged(
                generation.getTrip().getId(),
                generation,
                previousStatus,
                0
        );
    }

    @Transactional
    public void saveValidatedDraftAndComplete(
            Long userId,
            Long tripId,
            Long generationId,
            GroundedItineraryDraft draft,
            long latencyMillis
    ) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findWithTripById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));

        draftValidator.validate(trip, profile.getMustVisitPlaces(), draft);

        Instant now = Instant.now(clock);
        generation.markValidating(now);
        ItineraryEntity itinerary = itineraryRepository.save(ItineraryEntity.create(trip, generation, now));
        for (ItineraryDraftDay responseDay : draft.days()) {
            ItineraryDayEntity day = itineraryDayRepository.save(ItineraryDayEntity.create(
                    itinerary,
                    responseDay.day(),
                    trip.getStartDate().plusDays(responseDay.day() - 1L)
            ));
            for (ItineraryDraftItem responseItem : responseDay.items()) {
                itineraryItemRepository.save(ItineraryItemEntity.create(
                        day,
                        responseItem.sequence(),
                        draftValidator.normalizePlaceId(responseItem.placeId()),
                        draftValidator.parseTime(responseItem.startTime()),
                        responseItem.durationMinutes(),
                        ItineraryItemCreatedSource.AI_DRAFT
                ));
            }
        }
        ItineraryGenerationStatus previousStatus = generation.getStatus();
        generation.recordProviderLatency(latencyMillis);
        generation.markCompleted(now);
        publishStatusChanged(trip.getId(), generation, previousStatus, 0);
    }

    @Transactional(readOnly = true)
    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        return new ItineraryGenerationDetailResponse(
                generation.getId().toString(),
                trip.getId().toString(),
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
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        return generationRepository.findFirstByTrip_IdOrderByCreatedAtDesc(trip.getId())
                .map(generation -> toDetailResponse(trip, generation));
    }

    @Transactional(readOnly = true)
    public AiRequestContext loadAiRequestContext(Long userId, Long tripId, Long generationId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findWithTripById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
        return new AiRequestContext(generation, profile);
    }

    private ItineraryGenerationEntity findGeneration(Long generationId) {
        return generationRepository.findById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
    }

    private ItineraryGenerationDetailResponse toDetailResponse(TripEntity trip, ItineraryGenerationEntity generation) {
        return new ItineraryGenerationDetailResponse(
                generation.getId().toString(),
                trip.getId().toString(),
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

    private ResolvedDestination toResolvedDestination(TripEntity trip) {
        if (trip.getDestinationLatitude() == null || trip.getDestinationLongitude() == null) {
            throw new ItineraryException(ItineraryErrorCode.DESTINATION_NOT_RESOLVED);
        }
        GeoPoint location = new GeoPoint(trip.getDestinationLatitude(), trip.getDestinationLongitude());
        GeoViewport viewport = null;
        if (trip.getDestinationViewportLowLatitude() != null
                && trip.getDestinationViewportLowLongitude() != null
                && trip.getDestinationViewportHighLatitude() != null
                && trip.getDestinationViewportHighLongitude() != null) {
            viewport = new GeoViewport(
                    new GeoPoint(trip.getDestinationViewportLowLatitude(), trip.getDestinationViewportLowLongitude()),
                    new GeoPoint(trip.getDestinationViewportHighLatitude(), trip.getDestinationViewportHighLongitude())
            );
        }
        return new ResolvedDestination(
                trip.getDestinationPlaceId(),
                trip.getDestination(),
                trip.getDestinationFormattedAddress(),
                location,
                viewport,
                trip.getDestinationTypes(),
                trip.getDestinationPrimaryType()
        );
    }

    private ItineraryGenerationContext toGenerationContext(
            TripEntity trip,
            ItineraryGenerationEntity generation,
            TripPlanningProfileEntity profile
    ) {
        GeoPoint accommodationLocation = null;
        if (profile.getAccommodationLatitude() != null && profile.getAccommodationLongitude() != null) {
            accommodationLocation = new GeoPoint(
                    profile.getAccommodationLatitude(),
                    profile.getAccommodationLongitude()
            );
        }
        return new ItineraryGenerationContext(
                generation.getId(),
                trip.getId(),
                trip.getTitle(),
                generation.getPromptVersion(),
                generation.getSchemaVersion(),
                generation.getRequestFingerprint(),
                new ItineraryGenerationContext.Destination(
                        trip.getDestinationPlaceId(),
                        trip.getDestination(),
                        trip.getDestinationFormattedAddress(),
                        toResolvedDestination(trip).location(),
                        toResolvedDestination(trip).viewport(),
                        trip.getDestinationTypes(),
                        trip.getDestinationPrimaryType()
                ),
                trip.getStartDate(),
                trip.getEndDate(),
                new ItineraryGenerationContext.Profile(
                        profile.getCompanionCount(),
                        profile.getCompanionType(),
                        profile.isHasChildren(),
                        profile.getChildCount(),
                        profile.getChildAgeGroup(),
                        profile.isHasSeniors(),
                        profile.getSeniorCount(),
                        profile.getCurrencyCode(),
                        profile.getBudgetAmount(),
                        profile.getBudgetLevel(),
                        profile.getIncludedBudgetItems(),
                        profile.getTravelPace(),
                        profile.getInterests(),
                        profile.getPrimaryTransportMode(),
                        profile.getSecondaryTransportModes(),
                        profile.getAccommodationMode(),
                        profile.getAccommodationArea(),
                        profile.getAccommodationPlaceId(),
                        profile.getAccommodationName(),
                        profile.getAccommodationFormattedAddress(),
                        accommodationLocation,
                        profile.getCheckInTime(),
                        profile.getCheckOutTime(),
                        profile.getDailyStartTime(),
                        profile.getDailyEndTime(),
                        profile.getMustVisitPlaces(),
                        profile.getAvoidConditions(),
                        profile.getFreeRequest()
                )
        );
    }

    public record GenerationCollectionContext(
            Long generationId,
            ResolvedDestination destination,
            TripPlanningProfileEntity profile
    ) {
    }

    public record AiRequestContext(
            ItineraryGenerationEntity generation,
            TripPlanningProfileEntity profile
    ) {
    }
}
