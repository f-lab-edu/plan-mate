package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.recommendation.api.CandidateRecommendationRequest;
import com.planmate.recommendation.api.RecommendedPlaceCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationCandidateSnapshotMapperTest {

    private final GenerationCandidateSnapshotMapper mapper = new GenerationCandidateSnapshotMapper();

    @Test
    void mapsRecommendedCandidateToItineraryOwnedSnapshot() {
        RecommendedPlaceCandidate candidate = new RecommendedPlaceCandidate(
                2,
                "place-1",
                "Place",
                "Address",
                new CandidateRecommendationRequest.Location(35.0, 135.0),
                "museum",
                List.of("museum", "point_of_interest"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("MUST_VISIT"),
                true,
                120.0,
                42.5
        );

        GenerationCandidateSnapshot snapshot = mapper.map(candidate);

        assertThat(snapshot.rank()).isEqualTo(2);
        assertThat(snapshot.placeId()).isEqualTo("place-1");
        assertThat(snapshot.displayName()).isEqualTo("Place");
        assertThat(snapshot.formattedAddress()).isEqualTo("Address");
        assertThat(snapshot.location().latitude()).isEqualTo(35.0);
        assertThat(snapshot.location().longitude()).isEqualTo(135.0);
        assertThat(snapshot.primaryType()).isEqualTo("museum");
        assertThat(snapshot.types()).containsExactly("museum", "point_of_interest");
        assertThat(snapshot.businessStatus()).isEqualTo("OPERATIONAL");
        assertThat(snapshot.rating()).isEqualTo(4.5);
        assertThat(snapshot.userRatingCount()).isEqualTo(100);
        assertThat(snapshot.openingPeriods()).containsExactly("Mon 09:00-18:00");
        assertThat(snapshot.sourceCategories()).containsExactly("MUST_VISIT");
        assertThat(snapshot.forcedMustVisit()).isTrue();
        assertThat(snapshot.distanceMeters()).isEqualTo(120.0);
        assertThat(snapshot.score()).isEqualTo(42.5);
    }
}
