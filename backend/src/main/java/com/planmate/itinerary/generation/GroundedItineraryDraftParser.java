package com.planmate.itinerary.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.dto.GroundedItineraryDraft;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class GroundedItineraryDraftParser {

    private final ObjectMapper objectMapper;

    public GroundedItineraryDraftParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public GroundedItineraryDraft parse(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_RESPONSE_EMPTY,
                    false
            );
        }
        try {
            return objectMapper.readValue(extractJson(value), GroundedItineraryDraft.class);
        } catch (JsonProcessingException exception) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_RESPONSE_NOT_JSON,
                    false,
                    exception
            );
        }
    }

    private String extractJson(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("```")) {
            int firstLineEnd = trimmed.indexOf('\n');
            int fenceStart = firstLineEnd < 0 ? 0 : firstLineEnd + 1;
            int fenceEnd = trimmed.lastIndexOf("```");
            if (fenceEnd > fenceStart) {
                return trimmed.substring(fenceStart, fenceEnd).trim();
            }
        }
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }
}
