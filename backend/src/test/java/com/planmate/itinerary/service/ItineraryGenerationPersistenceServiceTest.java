package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.planmate.common.outbox.OutboxEventEntity;
import com.planmate.common.outbox.OutboxEventRepository;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.exception.ItineraryException;
import com.planmate.itinerary.realtime.ItineraryGenerationStatusChangedEvent;
import com.planmate.itinerary.repository.ItineraryGenerationRepository;
import com.planmate.trip.api.TripAccessChecker;
import com.planmate.trip.api.TripPlanningSnapshot;
import com.planmate.trip.api.TripPlanningSnapshotReader;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ItineraryGenerationRepository generationRepository;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private TripAccessChecker tripAccessChecker;

    @Mock
    private TripPlanningSnapshotReader tripPlanningSnapshotReader;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ItineraryGenerationPersistenceService service;

    @BeforeEach
    void setUp() {
        service = new ItineraryGenerationPersistenceService(
                generationRepository,
                outboxEventRepository,
                tripAccessChecker,
                tripPlanningSnapshotReader,
                Clock.fixed(NOW, ZoneOffset.UTC),
                eventPublisher
        );
    }

    @Test
    void createGenerationRequestStoresGenerationAndOutboxEventInOneServiceCall() {
        given(generationRepository.save(any(ItineraryGenerationEntity.class)))
                .willAnswer(invocation -> {
                    ItineraryGenerationEntity generation = invocation.getArgument(0);
                    ReflectionTestUtils.setField(generation, "id", 123L);
                    return generation;
                });

        ItineraryGenerationEntity generation = service.createGenerationRequest(
                7L,
                45L,
                ItineraryPromptService.PROMPT_VERSION
        );

        assertThat(generation.getId()).isEqualTo(123L);
        assertThat(generation.getTripId()).isEqualTo(45L);
        ArgumentCaptor<OutboxEventEntity> outboxCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        OutboxEventEntity outboxEvent = outboxCaptor.getValue();
        assertThat(outboxEvent.getId()).isNotNull();
        assertThat(outboxEvent.getAggregateType()).isEqualTo("ITINERARY_GENERATION");
        assertThat(outboxEvent.getAggregateId()).isEqualTo("123");
        assertThat(outboxEvent.getEventType()).isEqualTo("ITINERARY_GENERATION_REQUESTED");
        assertThat(outboxEvent.getPayload())
                .containsEntry("generationId", 123L)
                .containsEntry("tripId", 45L)
                .containsEntry("userId", 7L);
        assertThat(outboxEvent.getCreatedAt()).isEqualTo(NOW);
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void markCollectingIfCreatedMarksCreatedGenerationAndReturnsTrue() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        boolean result = service.markCollectingIfCreated(7L, 45L, 123L);

        assertThat(result).isTrue();
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
        verify(eventPublisher).publishEvent(any(Object.class));
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void markCollectingIfCreatedReturnsFalseForAlreadyProcessedGeneration() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markReady(NOW);
        given(generationRepository.findWithLockById(123L)).willReturn(Optional.of(generation));

        boolean result = service.markCollectingIfCreated(7L, 45L, 123L);

        assertThat(result).isFalse();
        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
        verifyNoInteractions(eventPublisher);
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void markFailedPublishesStatusChangedEvent() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markCollecting(NOW);
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));

        service.markFailed(123L, "GOOGLE_PLACES_UNAVAILABLE");

        ArgumentCaptor<ItineraryGenerationStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ItineraryGenerationStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.tripId()).isEqualTo(45L);
            assertThat(event.generationId()).isEqualTo(123L);
            assertThat(event.previousStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
            assertThat(event.status()).isEqualTo(ItineraryGenerationStatus.FAILED);
            assertThat(event.candidateCount()).isZero();
            assertThat(event.failureReason()).isEqualTo("GOOGLE_PLACES_UNAVAILABLE");
        });
    }

    @Test
    void getLatestReturnsLatestGenerationAfterTripAccessCheck() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        generation.markReady(NOW);
        given(generationRepository.findFirstByTripIdOrderByCreatedAtDesc(45L)).willReturn(Optional.of(generation));

        Optional<com.planmate.itinerary.dto.ItineraryGenerationDetailResponse> result = service.getLatest(7L, 45L);

        assertThat(result).isPresent()
                .get()
                .satisfies(response -> {
                    assertThat(response.generationId()).isEqualTo("123");
                    assertThat(response.tripId()).isEqualTo("45");
                    assertThat(response.status()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
                    assertThat(response.candidateCount()).isZero();
                });
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void getLatestReturnsEmptyWhenTripHasNoGeneration() {
        given(generationRepository.findFirstByTripIdOrderByCreatedAtDesc(45L)).willReturn(Optional.empty());

        Optional<com.planmate.itinerary.dto.ItineraryGenerationDetailResponse> result = service.getLatest(7L, 45L);

        assertThat(result).isEmpty();
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void loadAiRequestContextReturnsPlanningSnapshot() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        TripPlanningSnapshot snapshot = snapshot(45L);
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));
        given(tripPlanningSnapshotReader.findByTripId(45L)).willReturn(Optional.of(snapshot));

        ItineraryGenerationPersistenceService.AiRequestContext result = service.loadAiRequestContext(7L, 45L, 123L);

        assertThat(result.generation()).isSameAs(generation);
        assertThat(result.snapshot()).isSameAs(snapshot);
        verify(tripAccessChecker).checkAccessible(7L, 45L);
    }

    @Test
    void loadAiRequestContextThrowsWhenPlanningProfileIsMissing() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));
        given(tripPlanningSnapshotReader.findByTripId(45L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadAiRequestContext(7L, 45L, 123L))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Trip planning profile not found.");
    }

    @Test
    void loadAiRequestContextRejectsGenerationFromDifferentTrip() {
        ItineraryGenerationEntity generation = generation(123L, 45L);
        given(generationRepository.findById(123L)).willReturn(Optional.of(generation));

        assertThatThrownBy(() -> service.loadAiRequestContext(7L, 46L, 123L))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation not found.");
    }

    private ItineraryGenerationEntity generation(Long generationId, Long tripId) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(
                tripId,
                ItineraryPromptService.PROMPT_VERSION,
                NOW
        );
        ReflectionTestUtils.setField(generation, "id", generationId);
        return generation;
    }

    private TripPlanningSnapshot snapshot(Long tripId) {
        return new TripPlanningSnapshot(
                tripId,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                new TripPlanningSnapshot.Destination("place-kyoto", "Kyoto", "Kyoto, Japan", 35.0, 135.0, null, List.of("locality"), "locality"),
                new TripPlanningSnapshot.Companion(3, "FRIENDS", false, 0, null, false, 0),
                new TripPlanningSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Preference("BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new TripPlanningSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                java.time.LocalTime.of(8, 0),
                java.time.LocalTime.of(20, 0),
                List.of(),
                List.of(),
                null
        );
    }
}
