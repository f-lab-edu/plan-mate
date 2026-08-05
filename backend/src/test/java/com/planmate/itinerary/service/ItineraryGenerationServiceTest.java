package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.trip.api.TripPlanningSnapshot;
import com.planmate.trip.entity.TripEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ItineraryGenerationPersistenceService persistenceService;

    private ItineraryGenerationService service;

    @BeforeEach
    void setUp() {
        service = new ItineraryGenerationService(persistenceService);
    }

    @Test
    void createOnlyCreatesGenerationRequestAndDoesNotCollectCandidates() {
        ItineraryGenerationEntity generation = generation(123L, trip(45L));
        given(persistenceService.createGenerationRequest(7L, 45L, ItineraryPromptService.PROMPT_VERSION))
                .willReturn(generation);

        ItineraryGenerationCreateResponse response = service.create(7L, 45L);

        assertThat(response.generationId()).isEqualTo("123");
        assertThat(response.status()).isEqualTo(ItineraryGenerationStatus.CREATED);
        assertThat(response.candidateCount()).isZero();
        verify(persistenceService).createGenerationRequest(7L, 45L, ItineraryPromptService.PROMPT_VERSION);
        verify(persistenceService, never()).markCollecting(anyLong());
        verifyNoMoreInteractions(persistenceService);
    }

    @Test
    void collectCandidatesValidatesContextAndMarksReadyForPlanning() {
        given(persistenceService.loadCollectionContext(7L, 45L, 123L))
                .willReturn(new ItineraryGenerationPersistenceService.GenerationCollectionContext(123L, snapshot(45L)));

        service.collectCandidates(7L, 45L, 123L);

        verify(persistenceService).loadCollectionContext(7L, 45L, 123L);
        verify(persistenceService).markReadyForPlanning(123L);
    }

    private ItineraryGenerationEntity generation(Long generationId, TripEntity trip) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(
                trip,
                ItineraryPromptService.PROMPT_VERSION,
                NOW
        );
        ReflectionTestUtils.setField(generation, "id", generationId);
        return generation;
    }

    private TripEntity trip(Long tripId) {
        TripEntity trip = TripEntity.create(
                "Kyoto trip",
                "Kyoto",
                "place-kyoto",
                "Kyoto, Japan",
                35.0,
                135.0,
                null,
                null,
                null,
                null,
                List.of("locality"),
                "locality",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                null,
                NOW
        );
        ReflectionTestUtils.setField(trip, "id", tripId);
        return trip;
    }

    private TripPlanningSnapshot snapshot(Long tripId) {
        return new TripPlanningSnapshot(
                tripId,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new TripPlanningSnapshot.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        null,
                        List.of("locality"),
                        "locality"
                ),
                new TripPlanningSnapshot.Companion(3, "FRIENDS", false, 0, null, false, 0),
                new TripPlanningSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Preference("BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new TripPlanningSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(),
                List.of(),
                null
        );
    }
}
