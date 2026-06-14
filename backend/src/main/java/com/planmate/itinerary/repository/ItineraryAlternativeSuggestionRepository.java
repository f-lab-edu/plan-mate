package com.planmate.itinerary.repository;

import com.planmate.itinerary.entity.ItineraryAlternativeSuggestionEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItineraryAlternativeSuggestionRepository
        extends JpaRepository<ItineraryAlternativeSuggestionEntity, Long> {

    List<ItineraryAlternativeSuggestionEntity> findByItinerary_IdOrderByIdAsc(Long itineraryId);

}
