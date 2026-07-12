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
import com.planmate.itinerary.generation.ItineraryDraftGenerationException;
import com.planmate.itinerary.realtime.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualItineraryResponseService {

    private final TripRepository tripRepository;
    private final ItineraryGenerationRepository generationRepository;
    private final TripPlanningProfileRepository tripPlanningProfileRepository;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final GroundedItineraryDraftValidator draftValidator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public ManualItineraryResponseService(
            TripRepository tripRepository,
            ItineraryGenerationRepository generationRepository,
            TripPlanningProfileRepository tripPlanningProfileRepository,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            GroundedItineraryDraftValidator draftValidator,
            Clock clock,
            ApplicationEventPublisher eventPublisher
    ) {
        this.tripRepository = tripRepository;
        this.generationRepository = generationRepository;
        this.tripPlanningProfileRepository = tripPlanningProfileRepository;
        this.itineraryRepository = itineraryRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.draftValidator = draftValidator;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void submit(Long userId, Long tripId, Long generationId, GroundedItineraryDraft draft) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        ItineraryGenerationEntity generation = generationRepository.findWithTripById(generationId)
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND));
        if (!generation.getTrip().getId().equals(trip.getId())) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_FOUND);
        }
        if (generation.getStatus() != ItineraryGenerationStatus.READY_FOR_PLANNING) {
            throw new ItineraryException(ItineraryErrorCode.GENERATION_NOT_READY);
        }
        if (draft == null || !generation.getId().toString().equals(draft.generationId())) {
            throw invalid("generationId가 현재 생성 작업과 일치하지 않습니다.");
        }

        TripPlanningProfileEntity profile = tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                .orElseThrow(() -> new ItineraryException(ItineraryErrorCode.PLANNING_PROFILE_NOT_FOUND));
        validateDraft(trip, profile, draft);

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
        generation.markCompleted(now);
        eventPublisher.publishEvent(new ItineraryGenerationStatusChangedEvent(
                trip.getId(),
                generation.getId(),
                previousStatus,
                generation.getStatus(),
                0,
                generation.getFailureReason(),
                generation.getUpdatedAt()
        ));
    }

    private void validateDraft(
            TripEntity trip,
            TripPlanningProfileEntity profile,
            GroundedItineraryDraft draft
    ) {
        try {
            draftValidator.validate(trip, profile.getMustVisitPlaces(), draft);
        } catch (ItineraryDraftGenerationException exception) {
            throw invalid(exception.getMessage());
        }
    }

    private ItineraryException invalid(String message) {
        return new ItineraryException(ItineraryErrorCode.INVALID_AI_RESPONSE, message);
    }
}
