package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryRepository extends JpaRepository<ItineraryEntity, Long> {

    Optional<ItineraryEntity> findByTrip_Id(Long tripId);

}
