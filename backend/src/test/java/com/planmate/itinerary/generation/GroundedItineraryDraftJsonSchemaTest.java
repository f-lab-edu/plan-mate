package com.planmate.itinerary.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class GroundedItineraryDraftJsonSchemaTest {

    private final GroundedItineraryDraftJsonSchema jsonSchema = new GroundedItineraryDraftJsonSchema();

    @Test
    void schemaIncludesOnlyPersistableDraftItemFields() {
        Map<String, Object> schema = jsonSchema.toJsonSchema();

        Map<?, ?> rootProperties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> days = (Map<?, ?>) rootProperties.get("days");
        Map<?, ?> dayItems = (Map<?, ?>) days.get("items");
        Map<?, ?> dayProperties = (Map<?, ?>) dayItems.get("properties");
        Map<?, ?> items = (Map<?, ?>) dayProperties.get("items");
        Map<?, ?> itemSchema = (Map<?, ?>) items.get("items");
        @SuppressWarnings("unchecked")
        Map<String, Object> itemProperties = (Map<String, Object>) itemSchema.get("properties");

        assertThat(itemProperties)
                .containsOnlyKeys("sequence", "placeId", "startTime", "durationMinutes")
                .doesNotContainKeys("placeName", "address", "rating", "reason", "sourceLinks", "routeInfo");
    }
}
