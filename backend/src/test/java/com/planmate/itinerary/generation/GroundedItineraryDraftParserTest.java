package com.planmate.itinerary.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class GroundedItineraryDraftParserTest {

    private final GroundedItineraryDraftParser parser = new GroundedItineraryDraftParser(new ObjectMapper());

    @Test
    void parsesJsonObjectFromPlainText() {
        var draft = parser.parse("""
                {"generationId":"10","days":[{"day":1,"items":[{"sequence":1,"placeId":"place-1","startTime":"09:00","durationMinutes":90}]}]}
                """);

        assertThat(draft.generationId()).isEqualTo("10");
        assertThat(draft.days()).hasSize(1);
        assertThat(draft.days().get(0).items().get(0).placeId()).isEqualTo("place-1");
    }

    @Test
    void extractsJsonFromMarkdownFence() {
        var draft = parser.parse("""
                ```json
                {"generationId":"10","days":[{"day":1,"items":[{"sequence":1,"placeId":"place-1","startTime":"09:00","durationMinutes":90}]}]}
                ```
                """);

        assertThat(draft.generationId()).isEqualTo("10");
    }

    @Test
    void mapsInvalidJsonToGenerationException() {
        assertThatThrownBy(() -> parser.parse("일정입니다."))
                .isInstanceOfSatisfying(ItineraryDraftGenerationException.class, exception ->
                        assertThat(exception.failureCode())
                                .isEqualTo(ItineraryDraftGenerationFailureCode.AI_RESPONSE_NOT_JSON));
    }
}
