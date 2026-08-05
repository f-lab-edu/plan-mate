package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.domain.GenerationInputSnapshot;
import com.planmate.itinerary.dto.AiItineraryRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AiItineraryRequestFactoryTest {

    private final AiItineraryRequestFactory factory = new AiItineraryRequestFactory();

    @Test
    void createsExistingAiRequestShapeFromStoredInputSnapshot() {
        AiItineraryRequest request = factory.create(123L, snapshot());

        assertThat(request.generationId()).isEqualTo("123");
        assertThat(request.tripId()).isEqualTo("45");
        assertThat(request.destination()).satisfies(destination -> {
            assertThat(destination.placeId()).isEqualTo("place-kyoto");
            assertThat(destination.name()).isEqualTo("Kyoto");
            assertThat(destination.formattedAddress()).isEqualTo("Kyoto, Japan");
            assertThat(destination.latitude()).isEqualTo(35.0);
            assertThat(destination.longitude()).isEqualTo(135.0);
            assertThat(destination.types()).containsExactly("locality");
            assertThat(destination.primaryType()).isEqualTo("locality");
        });
        assertThat(request.startDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(request.endDate()).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(request.companion()).satisfies(companion -> {
            assertThat(companion.count()).isEqualTo(3);
            assertThat(companion.type()).isEqualTo("FRIENDS");
            assertThat(companion.hasChildren()).isFalse();
            assertThat(companion.hasSeniors()).isTrue();
        });
        assertThat(request.budget().currencyCode()).isEqualTo("KRW");
        assertThat(request.budget().includedItems()).containsExactly("FOOD", "LODGING");
        assertThat(request.travelPace()).isEqualTo("BALANCED");
        assertThat(request.interests()).containsExactly("FOOD", "SIGHTSEEING");
        assertThat(request.transportation().primaryMode()).isEqualTo("PUBLIC_TRANSIT");
        assertThat(request.transportation().secondaryModes()).containsExactly("WALK");
        assertThat(request.accommodation()).satisfies(accommodation -> {
            assertThat(accommodation.mode()).isEqualTo("PLACE_SEARCH");
            assertThat(accommodation.preferredArea()).isEqualTo("DOWNTOWN");
            assertThat(accommodation.name()).isEqualTo("Kyoto Hotel");
            assertThat(accommodation.checkInTime()).isEqualTo(LocalTime.of(15, 0));
            assertThat(accommodation.checkOutTime()).isEqualTo(LocalTime.of(11, 0));
        });
        assertThat(request.mustVisitPlaces())
                .extracting(AiItineraryRequest.MustVisitPlace::placeId)
                .containsExactly("must-1");
        assertThat(request.avoidConditions()).containsExactly("LONG_WALK");
        assertThat(request.freeRequest()).isEqualTo("Keep lunch flexible.");
        assertThat(request.rules()).isNotEmpty();
    }

    private GenerationInputSnapshot snapshot() {
        return new GenerationInputSnapshot(
                45L,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
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
                new GenerationInputSnapshot.Companion(3, "FRIENDS", false, 0, null, true, 1),
                new GenerationInputSnapshot.Budget("KRW", 1_000_000L, "BALANCED", List.of("FOOD", "LODGING")),
                new GenerationInputSnapshot.Preference("BALANCED", List.of("FOOD", "SIGHTSEEING")),
                new GenerationInputSnapshot.Transportation("PUBLIC_TRANSIT", List.of("WALK")),
                new GenerationInputSnapshot.Accommodation(
                        "PLACE_SEARCH",
                        "DOWNTOWN",
                        "hotel-place",
                        "Kyoto Hotel",
                        "Hotel address",
                        35.1,
                        135.1,
                        List.of("lodging"),
                        "lodging",
                        LocalTime.of(15, 0),
                        LocalTime.of(11, 0)
                ),
                LocalTime.of(8, 0),
                LocalTime.of(20, 0),
                List.of(
                        new GenerationInputSnapshot.MustVisitPlace(
                                "must-1",
                                "Kiyomizu",
                                "Kiyomizu address",
                                35.0,
                                135.0,
                                List.of("tourist_attraction"),
                                "tourist_attraction"
                        ),
                        new GenerationInputSnapshot.MustVisitPlace(
                                "must-2",
                                "Unresolved",
                                null,
                                null,
                                null,
                                List.of(),
                                null
                        )
                ),
                List.of("LONG_WALK"),
                "Keep lunch flexible."
        );
    }
}
