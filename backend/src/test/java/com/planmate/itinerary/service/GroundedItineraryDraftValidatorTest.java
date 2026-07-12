package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.itinerary.dto.GroundedItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.generation.ItineraryDraftGenerationException;
import com.planmate.itinerary.generation.ItineraryDraftGenerationFailureCode;
import com.planmate.trip.domain.MustVisitPlaceSnapshot;
import com.planmate.trip.entity.TripEntity;
import com.planmate.user.entity.UserEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class GroundedItineraryDraftValidatorTest {

    private GroundedItineraryDraftValidator validator;
    private TripEntity trip;

    @BeforeEach
    void setUp() {
        AiItineraryProperties properties = new AiItineraryProperties();
        properties.setMaxItemsPerDay(3);
        properties.setMaxRepeatPerPlace(1);
        validator = new GroundedItineraryDraftValidator(properties);
        trip = trip();
    }

    @Test
    void acceptsValidPlaceIdOnlyDraft() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-2"))
                )
        );

        assertThatCode(() -> validator.validate(trip, List.of(mustVisitPlace("place-1")), draft))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsWhenTripDayCountDoesNotMatch() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(day(1, item(1, "place-1")))
        );

        assertValidationFailure(
                () -> validator.validate(trip, List.of(), draft),
                "AI 일정의 days 개수는 여행 일수와 일치해야 합니다."
        );
    }

    @Test
    void rejectsDuplicateSequenceInSameDay() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(
                        new ItineraryDraftDay(1, List.of(item(1, "place-1"), item(1, "place-2"))),
                        day(2, item(1, "place-3"))
                )
        );

        assertValidationFailure(
                () -> validator.validate(trip, List.of(), draft),
                "AI 일정의 sequence가 중복되었거나 올바르지 않습니다."
        );
    }

    @Test
    void rejectsMissingPlaceId() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, " ")),
                        day(2, item(1, "place-2"))
                )
        );

        assertValidationFailure(
                () -> validator.validate(trip, List.of(), draft),
                "AI 일정 항목에는 placeId가 필요합니다."
        );
    }

    @Test
    void rejectsMissingMustVisitPlace() {
        GroundedItineraryDraft draft = new GroundedItineraryDraft(
                "10",
                List.of(
                        day(1, item(1, "place-1")),
                        day(2, item(1, "place-2"))
                )
        );

        assertValidationFailure(
                () -> validator.validate(trip, List.of(mustVisitPlace("place-3")), draft),
                "AI 일정에는 필수 방문지가 포함되어야 합니다."
        );
    }

    private void assertValidationFailure(Runnable runnable, String message) {
        assertThatThrownBy(runnable::run)
                .isInstanceOfSatisfying(ItineraryDraftGenerationException.class, exception -> {
                    assertThat(exception.failureCode())
                            .isEqualTo(ItineraryDraftGenerationFailureCode.AI_RESPONSE_VALIDATION_FAILED);
                    assertThat(exception.getMessage()).isEqualTo(message);
                    assertThat(exception.retryable()).isFalse();
                });
    }

    private ItineraryDraftDay day(int day, ItineraryDraftItem item) {
        return new ItineraryDraftDay(day, List.of(item));
    }

    private ItineraryDraftItem item(int sequence, String placeId) {
        return new ItineraryDraftItem(sequence, placeId, "09:00", 120);
    }

    private MustVisitPlaceSnapshot mustVisitPlace(String placeId) {
        return new MustVisitPlaceSnapshot(
                placeId,
                "장소",
                "주소",
                35.0,
                135.0,
                List.of("tourist_attraction"),
                "tourist_attraction"
        );
    }

    private TripEntity trip() {
        UserEntity owner = UserEntity.createOauthUser(
                "owner@example.com",
                "owner@example.com",
                "owner",
                true,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        TripEntity entity = TripEntity.create(
                "Trip",
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
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10),
                owner,
                Instant.parse("2026-01-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(entity, "id", 1L);
        return entity;
    }
}
