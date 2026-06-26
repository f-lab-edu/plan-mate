package com.planmate.trip.service;

import com.planmate.place.dto.ResolvedDestination;
import com.planmate.place.service.GooglePlacesService;
import com.planmate.itinerary.service.ItineraryQueryService;
import com.planmate.trip.dto.TripCreateRequest;
import com.planmate.trip.dto.TripDestinationResponse;
import com.planmate.trip.dto.TripDetailResponse;
import com.planmate.trip.dto.TripMemberResponse;
import com.planmate.trip.dto.TripPlanningProfileResponse;
import com.planmate.trip.dto.TripStatus;
import com.planmate.trip.dto.TripSummaryResponse;
import com.planmate.trip.entity.TripEntity;
import com.planmate.trip.entity.TripMemberEntity;
import com.planmate.trip.entity.TripPlanningProfileEntity;
import com.planmate.trip.exception.TripNotFoundException;
import com.planmate.trip.repository.TripMemberRepository;
import com.planmate.trip.repository.TripPlanningProfileRepository;
import com.planmate.trip.repository.TripRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final TripPlanningProfileRepository tripPlanningProfileRepository;
    private final TripCreationPersistenceService tripCreationPersistenceService;
    private final ItineraryQueryService itineraryQueryService;
    private final GooglePlacesService googlePlacesService;
    private final Clock clock;

    public TripService(
            TripRepository tripRepository,
            TripMemberRepository tripMemberRepository,
            TripPlanningProfileRepository tripPlanningProfileRepository,
            TripCreationPersistenceService tripCreationPersistenceService,
            ItineraryQueryService itineraryQueryService,
            GooglePlacesService googlePlacesService,
            Clock clock
    ) {
        this.tripRepository = tripRepository;
        this.tripMemberRepository = tripMemberRepository;
        this.tripPlanningProfileRepository = tripPlanningProfileRepository;
        this.tripCreationPersistenceService = tripCreationPersistenceService;
        this.itineraryQueryService = itineraryQueryService;
        this.googlePlacesService = googlePlacesService;
        this.clock = clock;
    }

    public TripSummaryResponse create(Long userId, TripCreateRequest request) {
        String destinationPlaceId = request.destinationPlaceId().trim();
        ResolvedDestination destination = googlePlacesService.resolveDestination(destinationPlaceId, "ko");
        TripEntity trip = tripCreationPersistenceService.create(userId, request, destination);

        return toSummaryResponse(trip, 1);
    }

    @Transactional(readOnly = true)
    public List<TripSummaryResponse> listMine(Long userId) {
        return tripMemberRepository.findByUser_IdOrderByTrip_CreatedAtDesc(userId)
                .stream()
                .map(member -> {
                    TripEntity trip = member.getTrip();
                    return toSummaryResponse(trip, tripMemberRepository.countByTrip_Id(trip.getId()));
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public TripDetailResponse getDetail(Long userId, Long tripId) {
        TripEntity trip = tripRepository.findAccessibleTrip(tripId, userId)
                .orElseThrow(TripNotFoundException::new);
        List<TripMemberEntity> members = tripMemberRepository.findByTrip_IdOrderByCreatedAtAsc(trip.getId());
        List<TripMemberResponse> memberResponses = members.stream()
                .map(member -> new TripMemberResponse(
                        member.getUser().getId(),
                        member.getUser().getNickname(),
                        member.getUser().getProfileImageUrl(),
                        member.getRole()
                ))
                .toList();

        return new TripDetailResponse(
                trip.getId().toString(),
                trip.getTitle(),
                trip.getDestination(),
                trip.getDestinationPlaceId(),
                trip.getStartDate(),
                trip.getEndDate(),
                statusOf(trip),
                members.size(),
                trip.getCreatedAt(),
                memberResponses,
                toDestinationResponse(trip),
                tripPlanningProfileRepository.findByTrip_Id(trip.getId())
                        .map(this::toPlanningProfileResponse)
                        .orElse(null),
                itineraryQueryService.listTripItineraries(trip.getId())
        );
    }

    private TripSummaryResponse toSummaryResponse(TripEntity trip, long memberCount) {
        return new TripSummaryResponse(
                trip.getId().toString(),
                trip.getTitle(),
                trip.getDestination(),
                trip.getDestinationPlaceId(),
                trip.getStartDate(),
                trip.getEndDate(),
                statusOf(trip),
                memberCount,
                trip.getCreatedAt()
        );
    }

    private TripStatus statusOf(TripEntity trip) {
        LocalDate today = LocalDate.now(clock);
        if (trip.getEndDate().isBefore(today)) {
            return TripStatus.COMPLETED;
        }
        if (trip.getStartDate().isAfter(today)) {
            return TripStatus.UPCOMING;
        }
        return TripStatus.PLANNING;
    }

    private TripDestinationResponse toDestinationResponse(TripEntity trip) {
        return new TripDestinationResponse(
                trip.getDestinationPlaceId(),
                trip.getDestination(),
                trip.getDestinationFormattedAddress(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                trip.getDestinationViewportLowLatitude(),
                trip.getDestinationViewportLowLongitude(),
                trip.getDestinationViewportHighLatitude(),
                trip.getDestinationViewportHighLongitude(),
                trip.getDestinationTypes(),
                trip.getDestinationPrimaryType()
        );
    }

    private TripPlanningProfileResponse toPlanningProfileResponse(TripPlanningProfileEntity profile) {
        return new TripPlanningProfileResponse(
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
                profile.getAccommodationName(),
                profile.getCheckInTime(),
                profile.getCheckOutTime(),
                profile.getMustVisitPlaces(),
                profile.getAvoidConditions(),
                profile.getFreeRequest()
        );
    }

}
