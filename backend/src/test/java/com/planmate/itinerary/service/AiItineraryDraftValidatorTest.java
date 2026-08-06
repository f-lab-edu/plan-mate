package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.exception.ItineraryErrorCode;
import com.planmate.itinerary.exception.ItineraryException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AiItineraryDraftValidatorTest {

    private final AiItineraryDraftValidator validator = new AiItineraryDraftValidator();

    @Test
    void acceptsV1DraftWithoutCandidateSnapshotsAndWithoutWhitelist() {
        assertThatCode(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(mustVisitPlace("place-1"))),
                List.of(),
                draft("place-1", "outside-v1-candidate")
        )).doesNotThrowAnyException();
    }

    @Test
    void validatesV1MustVisitPlacesFromInputSnapshot() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(mustVisitPlace("place-1"), mustVisitPlace("place-3"))),
                List.of(),
                draft("place-1", "place-2")
        ), "mustVisitPlaceIds");
    }

    @Test
    void acceptsV2DraftWhenEveryPlaceIdIsAllowedAndForcedCandidatesAreIncluded() {
        assertThatCode(() -> validator.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of(mustVisitPlace("not-a-v2-rule"))),
                List.of(candidate(1, "place-1", true), candidate(2, "place-2", false)),
                draft("place-1", "place-2")
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsV2DraftWhenPlaceIdIsOutsideWhitelist() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "place-1", false), candidate(2, "place-2", false)),
                draft("place-1", "outside")
        ), "whitelist");
    }

    @Test
    void trimsPlaceIdsBeforeV2WhitelistComparison() {
        assertThatCode(() -> validator.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, " place-1 ", true), candidate(2, "place-2", false)),
                draft(" place-1 ", "place-2")
        )).doesNotThrowAnyException();
    }

    @Test
    void keepsV2WhitelistComparisonCaseSensitive() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "place-1", false), candidate(2, "place-2", false)),
                draft("PLACE-1", "place-2")
        ), "whitelist");
    }

    @Test
    void rejectsV2DraftWhenForcedCandidateIsMissing() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(candidate(1, "place-1", true), candidate(2, "place-2", false)),
                draft("place-2", "place-2")
        ), "forcedMustVisit");
    }

    @Test
    void doesNotRequireNonForcedV2CandidatesOrInputMustVisitPlaces() {
        assertThatCode(() -> validator.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of(mustVisitPlace("input-must-visit"))),
                List.of(candidate(1, "place-1", true), candidate(2, "place-2", false)),
                draft("place-1", "place-1")
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsV2DraftWhenCandidateSnapshotsAreMissing() {
        assertThatThrownBy(() -> validator.validate(
                ItineraryPromptService.VERSION_V2,
                inputSnapshot(List.of()),
                List.of(),
                draft("place-1", "place-2")
        ))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary generation candidate snapshots not found.")
                .satisfies(exception -> {
                    ItineraryException itineraryException = (ItineraryException) exception;
                    assertThat(itineraryException.status()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(itineraryException.code()).isEqualTo(ItineraryErrorCode.GENERATION_CANDIDATES_NOT_FOUND.code());
                });
    }

    @Test
    void rejectsDraftWhenDayCountDoesNotMatchTripDuration() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(mustVisitPlace("place-1"))),
                List.of(),
                new AiItineraryDraft("10", List.of(day(1, item(1, "place-1"))))
        ), "days");
    }

    @Test
    void rejectsDraftWhenDayIsDuplicatedOrOutOfRange() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(mustVisitPlace("place-1"))),
                List.of(),
                new AiItineraryDraft("10", List.of(
                        day(1, item(1, "place-1")),
                        day(1, item(1, "place-2"))
                ))
        ), "day");
    }

    @Test
    void rejectsDraftWhenDayItemsAreMissing() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(mustVisitPlace("place-1"))),
                List.of(),
                new AiItineraryDraft("10", List.of(
                        day(1, item(1, "place-1")),
                        new ItineraryDraftDay(2, List.of())
                ))
        ), "items");
    }

    @Test
    void rejectsDraftWhenSequenceIsDuplicatedOrOutOfRange() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of(mustVisitPlace("place-1"))),
                List.of(),
                new AiItineraryDraft("10", List.of(
                        new ItineraryDraftDay(1, List.of(item(1, "place-1"), item(1, "place-2"))),
                        day(2, item(1, "place-3"))
                ))
        ), "sequence");
    }

    @Test
    void rejectsDraftWhenPlaceIdIsBlank() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of()),
                List.of(),
                draft(" ", "place-2")
        ), "placeId");
    }

    @Test
    void rejectsDraftWhenStartTimeIsInvalid() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of()),
                List.of(),
                new AiItineraryDraft("10", List.of(
                        day(1, new ItineraryDraftItem(1, "place-1", "9am", 60)),
                        day(2, item(1, "place-2"))
                ))
        ), "startTime");
    }

    @Test
    void rejectsDraftWhenDurationMinutesIsNotPositive() {
        assertValidationFailed(() -> validator.validate(
                ItineraryPromptService.VERSION_V1,
                inputSnapshot(List.of()),
                List.of(),
                new AiItineraryDraft("10", List.of(
                        day(1, new ItineraryDraftItem(1, "place-1", "09:00", 0)),
                        day(2, item(1, "place-2"))
                ))
        ), "durationMinutes");
    }

    @Test
    void rejectsUnsupportedPromptVersion() {
        assertThatThrownBy(() -> validator.validate(
                "itinerary-plan-v999",
                inputSnapshot(List.of()),
                List.of(),
                draft("place-1", "place-2")
        ))
                .isInstanceOf(ItineraryException.class)
                .hasMessage("Itinerary prompt version is not supported.")
                .satisfies(exception -> assertThat(((ItineraryException) exception).code())
                        .isEqualTo(ItineraryErrorCode.UNSUPPORTED_PROMPT_VERSION.code()));
    }

    private void assertValidationFailed(ThrowingCallable callable, String messagePart) {
        assertThatThrownBy(callable)
                .isInstanceOf(ItineraryException.class)
                .hasMessageContaining(messagePart)
                .satisfies(exception -> {
                    ItineraryException itineraryException = (ItineraryException) exception;
                    assertThat(itineraryException.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(itineraryException.code()).isEqualTo(ItineraryErrorCode.AI_RESPONSE_VALIDATION_FAILED.code());
                });
    }

    private AiItineraryDraft draft(String day1PlaceId, String day2PlaceId) {
        return new AiItineraryDraft("10", List.of(
                day(1, item(1, day1PlaceId)),
                day(2, item(1, day2PlaceId))
        ));
    }

    private ItineraryDraftDay day(int day, ItineraryDraftItem item) {
        return new ItineraryDraftDay(day, List.of(item));
    }

    private ItineraryDraftItem item(int sequence, String placeId) {
        return new ItineraryDraftItem(sequence, placeId, "09:00", 60);
    }

    private GenerationInputSnapshot inputSnapshot(List<GenerationInputSnapshot.MustVisitPlace> mustVisitPlaces) {
        return new GenerationInputSnapshot(
                1L,
                LocalDate.of(2026, 10, 9),
                LocalDate.of(2026, 10, 10),
                new GenerationInputSnapshot.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        35.0,
                        135.0,
                        new GenerationInputSnapshot.Viewport(34.8, 134.8, 35.2, 135.2),
                        List.of("locality"),
                        "locality"
                ),
                new GenerationInputSnapshot.Companion(2, "FRIENDS", false, 0, null, false, 0),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Preference("BALANCED", List.of("FOOD")),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new GenerationInputSnapshot.Accommodation("UNDECIDED", null, null, null, null, null, null, List.of(), null, null, null),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                mustVisitPlaces,
                List.of(),
                null
        );
    }

    private GenerationInputSnapshot.MustVisitPlace mustVisitPlace(String placeId) {
        return new GenerationInputSnapshot.MustVisitPlace(
                placeId,
                "Place",
                "Address",
                35.0,
                135.0,
                List.of("tourist_attraction"),
                "tourist_attraction"
        );
    }

    private GenerationCandidateSnapshot candidate(int rank, String placeId, boolean forcedMustVisit) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Place " + rank,
                "Address " + rank,
                new GenerationCandidateSnapshot.Location(35.0 + rank, 135.0 + rank),
                "tourist_attraction",
                List.of("tourist_attraction"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of(forcedMustVisit ? "MUST_VISIT" : "CORE_VISIT"),
                forcedMustVisit,
                100.0,
                42.5
        );
    }
}
