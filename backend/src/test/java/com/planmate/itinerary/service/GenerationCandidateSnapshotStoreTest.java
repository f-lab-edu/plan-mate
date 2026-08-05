package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.repository.GenerationCandidateSnapshotRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class GenerationCandidateSnapshotStoreTest {

    private final GenerationCandidateSnapshotRepository repository = Mockito.mock(GenerationCandidateSnapshotRepository.class);
    private final GenerationCandidateSnapshotStore store = new GenerationCandidateSnapshotStore(repository);

    @Test
    void replacesExistingSnapshotsAndReturnsSavedCount() {
        ItineraryGenerationEntity generation = generation();
        List<GenerationCandidateSnapshot> snapshots = List.of(snapshot(1, "place-1"), snapshot(2, "place-2"));
        given(repository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        int savedCount = store.replaceAll(generation, snapshots);

        assertThat(savedCount).isEqualTo(2);
        InOrder inOrder = inOrder(repository);
        inOrder.verify(repository).deleteByGeneration_Id(123L);
        inOrder.verify(repository).flush();
        ArgumentCaptor<List<GenerationCandidateSnapshotEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(GenerationCandidateSnapshotEntity::getPlaceId)
                .containsExactly("place-1", "place-2");
    }

    @Test
    void normalizesNullSnapshotListToEmptyReplacement() {
        ItineraryGenerationEntity generation = generation();
        given(repository.saveAll(anyList())).willAnswer(invocation -> invocation.getArgument(0));

        int savedCount = store.replaceAll(generation, null);

        assertThat(savedCount).isZero();
        ArgumentCaptor<List<GenerationCandidateSnapshotEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).isEmpty();
    }

    @Test
    void countsSnapshotsByGenerationId() {
        given(repository.countByGeneration_Id(123L)).willReturn(2L);

        assertThat(store.countByGenerationId(123L)).isEqualTo(2L);
    }

    private ItineraryGenerationEntity generation() {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(
                45L,
                "test",
                Instant.parse("2026-01-01T00:00:00Z")
        );
        ReflectionTestUtils.setField(generation, "id", 123L);
        return generation;
    }

    private GenerationCandidateSnapshot snapshot(int rank, String placeId) {
        return new GenerationCandidateSnapshot(
                rank,
                placeId,
                "Place",
                "Address",
                new GenerationCandidateSnapshot.Location(35.0, 135.0),
                "museum",
                List.of("museum"),
                "OPERATIONAL",
                4.5,
                100,
                List.of("Mon 09:00-18:00"),
                List.of("CORE_VISIT"),
                false,
                120.0,
                42.5
        );
    }
}
