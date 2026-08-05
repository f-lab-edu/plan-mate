package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.dto.GroundedItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.entity.ItineraryEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.entity.ItineraryItemEntity;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class ManualItineraryResponseServiceTest {

    private final TripAccessChecker tripAccessChecker = Mockito.mock(TripAccessChecker.class);
    private final TripPlanningSnapshotReader tripPlanningSnapshotReader = Mockito.mock(TripPlanningSnapshotReader.class);
    private final ItineraryGenerationRepository generationRepository = Mockito.mock(ItineraryGenerationRepository.class);
    private final ItineraryRepository itineraryRepository = Mockito.mock(ItineraryRepository.class);
    private final ItineraryDayRepository itineraryDayRepository = Mockito.mock(ItineraryDayRepository.class);
    private final ItineraryItemRepository itineraryItemRepository = Mockito.mock(ItineraryItemRepository.class);
    private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final ManualItineraryResponseService service = new ManualItineraryResponseService(
            tripAccessChecker,
            tripPlanningSnapshotReader,
            generationRepository,
            itineraryRepository,
            itineraryDayRepository,
            itineraryItemRepository,
            clock,
            eventPublisher
    );

    private ItineraryGenerationEntity generation;

    @BeforeEach
    void setUp() {
        generation = ItineraryGenerationEntity.create(1L, ItineraryPromptService.PROMPT_VERSION, Instant.now(clock));
        generation.markReady(Instant.now(clock));
        ReflectionTestUtils.setField(generation, "id", 10L);

        given(generationRepository.findById(10L)).willReturn(Optional.of(generation));
        given(tripPlanningSnapshotReader.findByTripId(1L)).willReturn(Optional.of(snapshot()));
    }

    @Test
    void savesPlaceIdOnlyDraftAndPublishesCompletedEvent() {
        given(itineraryRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryDayRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(itineraryItemRepository.save(Mockito.any())).willAnswer(invocation -> invocation.getArgument(0));

        service.submit(99L, 1L, 10L, validDraft());

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
        ArgumentCaptor<ItineraryItemEntity> itemCaptor = ArgumentCaptor.forClass(ItineraryItemEntity.class);
        verify(itineraryItemRepository, Mockito.times(2)).save(itemCaptor.capture());
        assertThat(itemCaptor.getAllValues())
                .extracting(ItineraryItemEntity::getPlaceId)
                .containsExactly("place-1", "place-2");

        ArgumentCaptor<ItineraryGenerationStatusChangedEvent> eventCaptor =
                ArgumentCaptor.forClass(ItineraryGenerationStatusChangedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue()).satisfies(event -> {
            assertThat(event.tripId()).isEqualTo(1L);
            assertThat(event.generationId()).isEqualTo(10L);
            assertThat(event.previousStatus()).isEqualTo(ItineraryGenerationStatus.VALIDATING);
            assertThat(event.status()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
            assertThat(event.candidateCount()).isZero();
            assertThat(event.failureReason()).isNull();
        });
        ArgumentCaptor<ItineraryEntity> itineraryCaptor = ArgumentCaptor.forClass(ItineraryEntity.class);
        verify(itineraryRepository).save(itineraryCaptor.capture());
        assertThat(itineraryCaptor.getValue().getTripId()).isEqualTo(generation.getTripId());
        assertThat(itineraryCaptor.getValue().getGeneration()).isSameAs(generation);
        verify(tripAccessChecker).checkAccessible(99L, 1L);
    }

    @Test
    void rejectsDraftWhenGenerationBelongsToDifferentTrip() {
        assertThatThrownBy(() -> service.submit(99L, 2L, 10L, validDraft()))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation not found.");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    @Test
    void rejectsDraftWhenDayCountDoesNotMatchTripDuration() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(day(1, item(1, "place-1")))
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(ItineraryException.class)
                .hasMessageContaining("days");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    @Test
    void rejectsDraftWhenMustVisitPlaceIsMissing() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-3"))
                )
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(ItineraryException.class)
                .hasMessageContaining("mustVisitPlaceIds");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    @Test
    void rejectsDraftWhenStartTimeIsInvalid() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, new ItineraryDraftItem(1, "place-1", "9am", 120)),
                        day(2, item(1, "place-2"))
                )
        );

        assertThatThrownBy(() -> service.submit(99L, 1L, 10L, draft))
                .isInstanceOf(ItineraryException.class)
                .hasMessageContaining("startTime");
        verify(itineraryRepository, never()).save(Mockito.any());
    }

    private GroundedItineraryDraft validDraft() {
        return new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-2"))
                )
        );
    }

    private ItineraryDraftDay day(int day, ItineraryDraftItem item) {
        return new ItineraryDraftDay(day, List.of(item));
    }

    private ItineraryDraftItem item(int sequence, String placeId) {
        return new ItineraryDraftItem(sequence, placeId, "09:00", 120);
    }

    private TripPlanningSnapshot snapshot() {
        return new TripPlanningSnapshot(
                1L,
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10),
                new TripPlanningSnapshot.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        new TripPlanningSnapshot.Viewport(34.8, 134.8, 35.2, 135.2),
                        List.of("locality"),
                        "locality"
                ),
                new TripPlanningSnapshot.Companion(2, "FRIENDS", false, 0, null, false, 0),
                new TripPlanningSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Preference("BALANCED", List.of("FOOD")),
                new TripPlanningSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new TripPlanningSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(mustVisitPlace("place-1"), mustVisitPlace("place-2")),
                List.of(),
                null
        );
    }

    private TripPlanningSnapshot.MustVisitPlace mustVisitPlace(String placeId) {
        return new TripPlanningSnapshot.MustVisitPlace(
                placeId,
                "Place",
                "Address",
                35.0,
                135.0,
                List.of("tourist_attraction"),
                "tourist_attraction"
        );
    }
}
