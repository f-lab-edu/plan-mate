package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryDayEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDayEntity, Long> {

    List<ItineraryDayEntity> findByItinerary_IdOrderByDayNumberAsc(Long itineraryId);

}
