package com.planmate.itinerary.generation;

import com.planmate.itinerary.dto.GroundedItineraryDraft;

public interface ItineraryDraftGenerator {

    String provider();

    GroundedItineraryDraft generate(ItineraryGenerationContext context);
}
