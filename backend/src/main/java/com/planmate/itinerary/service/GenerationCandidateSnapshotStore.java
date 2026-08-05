package com.planmate.itinerary.service;

import com.planmate.itinerary.domain.GenerationCandidateSnapshot;
import com.planmate.itinerary.entity.GenerationCandidateSnapshotEntity;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.repository.GenerationCandidateSnapshotRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class GenerationCandidateSnapshotStore {

    private final GenerationCandidateSnapshotRepository repository;

    public GenerationCandidateSnapshotStore(GenerationCandidateSnapshotRepository repository) {
        this.repository = repository;
    }

    public int replaceAll(
            ItineraryGenerationEntity generation,
            List<GenerationCandidateSnapshot> snapshots
    ) {
        List<GenerationCandidateSnapshot> safeSnapshots = snapshots == null
                ? List.of()
                : List.copyOf(snapshots);
        repository.deleteByGeneration_Id(generation.getId());
        repository.flush();
        List<GenerationCandidateSnapshotEntity> entities = safeSnapshots.stream()
                .map(snapshot -> GenerationCandidateSnapshotEntity.from(generation, snapshot))
                .toList();
        return repository.saveAll(entities).size();
    }

    public long countByGenerationId(Long generationId) {
        return repository.countByGeneration_Id(generationId);
    }
}
