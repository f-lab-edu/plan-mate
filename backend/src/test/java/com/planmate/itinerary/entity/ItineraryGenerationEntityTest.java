package com.planmate.itinerary.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.planmate.itinerary.api.ItineraryGenerationStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ItineraryGenerationEntityTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void createdGenerationCanMoveToCollecting() {
        ItineraryGenerationEntity generation = generation();

        generation.markCollecting(NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COLLECTING_CANDIDATES);
    }

    @Test
    void createdGenerationCannotMoveDirectlyToReady() {
        ItineraryGenerationEntity generation = generation();

        assertThatThrownBy(() -> generation.markReady(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected COLLECTING_CANDIDATES");
    }

    @Test
    void collectingGenerationCanMoveToReady() {
        ItineraryGenerationEntity generation = collectingGeneration();

        generation.markReady(NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.READY_FOR_PLANNING);
    }

    @Test
    void collectingGenerationCannotMoveDirectlyToCompleted() {
        ItineraryGenerationEntity generation = collectingGeneration();

        assertThatThrownBy(() -> generation.markCompleted(NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected READY_FOR_PLANNING");
    }

    @Test
    void readyGenerationCanMoveToCompleted() {
        ItineraryGenerationEntity generation = readyGeneration();

        generation.markCompleted(NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.COMPLETED);
    }

    @Test
    void completedGenerationCannotMoveToAnotherStatus() {
        ItineraryGenerationEntity generation = completedGeneration();

        assertThatThrownBy(() -> generation.markCollecting(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> generation.markReady(NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> generation.markCompleted(NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void createdGenerationCanMoveToFailed() {
        ItineraryGenerationEntity generation = generation();

        generation.markFailed("FAILED", NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.FAILED);
    }

    @Test
    void collectingGenerationCanMoveToFailed() {
        ItineraryGenerationEntity generation = collectingGeneration();

        generation.markFailed("FAILED", NOW);

        assertThat(generation.getStatus()).isEqualTo(ItineraryGenerationStatus.FAILED);
    }

    @Test
    void readyGenerationCannotMoveToFailed() {
        ItineraryGenerationEntity generation = readyGeneration();

        assertThatThrownBy(() -> generation.markFailed("FAILED", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected CREATED or COLLECTING_CANDIDATES");
    }

    @Test
    void completedGenerationCannotMoveToFailed() {
        ItineraryGenerationEntity generation = completedGeneration();

        assertThatThrownBy(() -> generation.markFailed("FAILED", NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected CREATED or COLLECTING_CANDIDATES");
    }

    private ItineraryGenerationEntity generation() {
        return ItineraryGenerationEntity.create(45L, "test", NOW);
    }

    private ItineraryGenerationEntity collectingGeneration() {
        ItineraryGenerationEntity generation = generation();
        generation.markCollecting(NOW);
        return generation;
    }

    private ItineraryGenerationEntity readyGeneration() {
        ItineraryGenerationEntity generation = collectingGeneration();
        generation.markReady(NOW);
        return generation;
    }

    private ItineraryGenerationEntity completedGeneration() {
        ItineraryGenerationEntity generation = readyGeneration();
        generation.markCompleted(NOW);
        return generation;
    }
}
