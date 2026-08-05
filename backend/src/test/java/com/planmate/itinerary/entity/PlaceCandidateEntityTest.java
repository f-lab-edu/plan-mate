package com.planmate.itinerary.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaceCandidateEntityTest {

    @Test
    void fromMapsRecommendedCandidateAndStoresCandidateRank() {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(1L, "test", Instant.parse("2026-08-05T00:00:00Z"));
        RecommendedPlaceCandidate candidate = candidate(
                3,
                List.of("CORE_VISIT", "MEAL"),
                List.of("Mon 09:00-18:00")
        );

        PlaceCandidateEntity entity = PlaceCandidateEntity.from(generation, candidate);

        assertThat(entity.getGeneration()).isSameAs(generation);
        assertThat(entity.getPlaceId()).isEqualTo("place-1");
        assertThat(entity.getName()).isEqualTo("Place");
        assertThat(entity.getAddress()).isEqualTo("Address");
        assertThat(entity.getLatitude()).isEqualTo(35.0);
        assertThat(entity.getLongitude()).isEqualTo(135.0);
        assertThat(entity.getPrimaryType()).isEqualTo("museum");
        assertThat(entity.getRating()).isEqualTo(4.5);
        assertThat(entity.getUserRatingCount()).isEqualTo(100);
        assertThat(entity.getSourceCategories()).containsExactly("CORE_VISIT", "MEAL");
        assertThat(entity.getOpeningPeriods()).containsExactly("Mon 09:00-18:00");
        assertThat(entity.getScore()).isEqualTo(42.5);
        assertThat(entity.getRank()).isEqualTo(3);
    }

    @Test
    void sourceCategoriesCanStoreExistingEnumNameJsonValuesAsStrings() {
        PlaceCandidateEntity entity = PlaceCandidateEntity.from(
                ItineraryGenerationEntity.create(1L, "test", Instant.parse("2026-08-05T00:00:00Z")),
                candidate(1, List.of("CORE_VISIT", "MEAL", "MUST_VISIT"), List.of())
        );

        assertThat(entity.getSourceCategories()).containsExactly("CORE_VISIT", "MEAL", "MUST_VISIT");
    }

    @Test
    void listFieldsAreDefensivelyCopied() {
        List<String> sourceCategories = new ArrayList<>(List.of("CORE_VISIT"));
        List<String> openingPeriods = new ArrayList<>(List.of("Mon 09:00-18:00"));

        PlaceCandidateEntity entity = PlaceCandidateEntity.from(
                ItineraryGenerationEntity.create(1L, "test", Instant.parse("2026-08-05T00:00:00Z")),
                candidate(1, sourceCategories, openingPeriods)
        );
        sourceCategories.add("MEAL");
        openingPeriods.clear();

        assertThat(entity.getSourceCategories()).containsExactly("CORE_VISIT");
        assertThat(entity.getOpeningPeriods()).containsExactly("Mon 09:00-18:00");
        assertThatThrownBy(() -> entity.getSourceCategories().add("MEAL"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private RecommendedPlaceCandidate candidate(
            int rank,
            List<String> sourceCategories,
            List<String> openingPeriods
    ) {
        return new RecommendedPlaceCandidate(
                rank,
                "place-1",
                "Place",
                "Address",
                new CandidateRecommendationRequest.Location(35.0, 135.0),
                "museum",
                List.of("museum"),
                "OPERATIONAL",
                4.5,
                100,
                openingPeriods,
                sourceCategories,
                sourceCategories.contains("MUST_VISIT"),
                100.0,
                42.5
        );
    }
}
