package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.api.ItineraryGenerationStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ItineraryGenerationRepository extends JpaRepository<ItineraryGenerationEntity, Long> {

    long countByStatus(ItineraryGenerationStatus status);

    long countByStatusAndUpdatedAtBefore(ItineraryGenerationStatus status, Instant updatedAt);

    Optional<ItineraryGenerationEntity> findFirstByTripIdOrderByCreatedAtDesc(Long tripId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ItineraryGenerationEntity> findWithLockById(Long id);
}
