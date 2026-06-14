package com.planmate.itinerary.service;

import com.planmate.itinerary.dto.BudgetSummaryResponse;
import com.planmate.itinerary.dto.EstimatedCostResponse;
import com.planmate.itinerary.dto.ItineraryAlternativeSuggestionResponse;
import com.planmate.itinerary.dto.ItineraryDayResponse;
import com.planmate.itinerary.dto.ItineraryItemResponse;
import com.planmate.itinerary.dto.ItineraryResponse;
import com.planmate.itinerary.dto.ParkingResponse;
import com.planmate.itinerary.dto.TransportResponse;
import com.planmate.itinerary.entity.ItineraryAlternativeSuggestionEntity;
import com.planmate.itinerary.entity.ItineraryDayEntity;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryItemEntity;
import com.planmate.itinerary.repository.ItineraryAlternativeSuggestionRepository;
import com.planmate.itinerary.repository.ItineraryDayRepository;
import com.planmate.itinerary.repository.ItineraryItemRepository;
import com.planmate.itinerary.repository.ItineraryRepository;
import com.planmate.trip.entity.TripEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ItineraryService {

    private static final String SOURCE_TYPE_MOCK = "MOCK";

    private final MockItinerarySampleProvider mockItinerarySampleProvider;
    private final ItineraryRepository itineraryRepository;
    private final ItineraryDayRepository itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final ItineraryAlternativeSuggestionRepository alternativeSuggestionRepository;

    public ItineraryService(
            MockItinerarySampleProvider mockItinerarySampleProvider,
            ItineraryRepository itineraryRepository,
            ItineraryDayRepository itineraryDayRepository,
            ItineraryItemRepository itineraryItemRepository,
            ItineraryAlternativeSuggestionRepository alternativeSuggestionRepository
    ) {
        this.mockItinerarySampleProvider = mockItinerarySampleProvider;
        this.itineraryRepository = itineraryRepository;
        this.itineraryDayRepository = itineraryDayRepository;
        this.itineraryItemRepository = itineraryItemRepository;
        this.alternativeSuggestionRepository = alternativeSuggestionRepository;
    }

    public void createFromMockSample(TripEntity trip, String mockSampleId, Instant now) {
        MockItinerarySample sample = mockItinerarySampleProvider.getById(mockSampleId);
        MockBudgetSummary budget = sample.budgetSummary();

        ItineraryEntity itinerary = itineraryRepository.save(ItineraryEntity.create(
                trip,
                SOURCE_TYPE_MOCK,
                sample.mockSampleId(),
                sample.summary(),
                budget.currency(),
                budget.totalMin(),
                budget.totalMax(),
                budget.perPersonMin(),
                budget.perPersonMax(),
                joinLines(budget.notes()),
                joinLines(sample.verificationWarnings()),
                now
        ));

        for (MockItineraryDay mockDay : sample.days()) {
            ItineraryDayEntity day = itineraryDayRepository.save(ItineraryDayEntity.create(
                    itinerary,
                    mockDay.day(),
                    LocalDate.parse(mockDay.dateLabel()),
                    mockDay.theme()
            ));

            for (MockItineraryItem mockItem : mockDay.items()) {
                itineraryItemRepository.save(toItemEntity(day, mockItem));
            }
        }

        for (MockAlternativeSuggestion suggestion : safeList(sample.alternativeSuggestions())) {
            alternativeSuggestionRepository.save(ItineraryAlternativeSuggestionEntity.create(
                    itinerary,
                    suggestion.target(),
                    suggestion.reason(),
                    joinLines(suggestion.candidates())
            ));
        }
    }

    public ItineraryResponse findResponseByTripId(Long tripId) {
        return itineraryRepository.findByTrip_Id(tripId)
                .map(this::toResponse)
                .orElse(null);
    }

    private ItineraryItemEntity toItemEntity(ItineraryDayEntity day, MockItineraryItem item) {
        MockEstimatedCost cost = item.estimatedCost();
        MockParking parking = item.parking();
        MockTransport transport = item.transport();
        boolean mapVisible = item.mapVisible() == null
                ? item.lat() != null && item.lng() != null
                : item.mapVisible();

        return ItineraryItemEntity.builder()
                .day(day)
                .itemOrder(item.order())
                .startTime(item.startTime())
                .endTime(item.endTime())
                .placeName(item.placeName())
                .category(item.category())
                .areaHint(item.areaHint())
                .description(item.description())
                .costMin(cost.min())
                .costMax(cost.max())
                .costIncluded(joinLines(cost.included()))
                .parkingRequired(parking.required())
                .parkingCostMin(parking.estimatedCostMin())
                .parkingCostMax(parking.estimatedCostMax())
                .parkingNotes(parking.notes())
                .transportMode(transport.mode())
                .transportFromPreviousMinutes(transport.fromPreviousMinutes())
                .transportCostMin(transport.estimatedCostMin())
                .transportCostMax(transport.estimatedCostMax())
                .transportNotes(transport.notes())
                .whyRecommended(item.whyRecommended())
                .needsVerification(item.needsVerification())
                .latitude(item.lat())
                .longitude(item.lng())
                .mapVisible(mapVisible)
                .build();
    }

    private ItineraryResponse toResponse(ItineraryEntity itinerary) {
        List<ItineraryDayResponse> days = itineraryDayRepository.findByItinerary_IdOrderByDayNumberAsc(itinerary.getId())
                .stream()
                .map(day -> new ItineraryDayResponse(
                        day.getDayNumber(),
                        day.getDateLabel(),
                        day.getTheme(),
                        itineraryItemRepository.findByDay_IdOrderByItemOrderAsc(day.getId())
                                .stream()
                                .map(this::toItemResponse)
                                .toList()
                ))
                .toList();

        List<ItineraryAlternativeSuggestionResponse> alternatives = alternativeSuggestionRepository
                .findByItinerary_IdOrderByIdAsc(itinerary.getId())
                .stream()
                .map(suggestion -> new ItineraryAlternativeSuggestionResponse(
                        suggestion.getTarget(),
                        suggestion.getReason(),
                        splitLines(suggestion.getCandidatesText())
                ))
                .toList();

        return new ItineraryResponse(
                itinerary.getSourceType(),
                itinerary.getSourceSampleId(),
                itinerary.getSummary(),
                new BudgetSummaryResponse(
                        itinerary.getBudgetCurrency(),
                        itinerary.getBudgetTotalMin(),
                        itinerary.getBudgetTotalMax(),
                        itinerary.getBudgetPerPersonMin(),
                        itinerary.getBudgetPerPersonMax(),
                        splitLines(itinerary.getBudgetNotes())
                ),
                days,
                alternatives,
                splitLines(itinerary.getVerificationWarnings())
        );
    }

    private ItineraryItemResponse toItemResponse(ItineraryItemEntity item) {
        return new ItineraryItemResponse(
                item.getId().toString(),
                item.getItemOrder(),
                item.getStartTime(),
                item.getEndTime(),
                item.getPlaceName(),
                item.getCategory(),
                item.getAreaHint(),
                item.getDescription(),
                new EstimatedCostResponse(item.getCostMin(), item.getCostMax(), splitLines(item.getCostIncluded())),
                new ParkingResponse(
                        item.isParkingRequired(),
                        item.getParkingCostMin(),
                        item.getParkingCostMax(),
                        item.getParkingNotes()
                ),
                new TransportResponse(
                        item.getTransportMode(),
                        item.getTransportFromPreviousMinutes(),
                        item.getTransportCostMin(),
                        item.getTransportCostMax(),
                        item.getTransportNotes()
                ),
                item.getWhyRecommended(),
                item.isNeedsVerification(),
                item.getLatitude(),
                item.getLongitude(),
                item.isMapVisible()
        );
    }

    private static String joinLines(List<String> values) {
        return String.join("\n", safeList(values));
    }

    private static List<String> splitLines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split("\\R"))
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

}
