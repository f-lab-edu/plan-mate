package com.planmate.itinerary.generation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class ItineraryDraftPromptBuilder {

    public static final String PROMPT_VERSION = "itinerary-grounded-plan-v1";
    private static final String TEMPLATE_PATH = "prompts/itinerary-grounded-plan-v1.txt";
    private static final String REQUEST_PLACEHOLDER = "{{ITINERARY_GENERATION_CONTEXT_JSON}}";

    private final ObjectMapper objectMapper;

    public ItineraryDraftPromptBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String build(ItineraryGenerationContext context) {
        return loadTemplate().replace(REQUEST_PLACEHOLDER, writeJson(promptContext(context)));
    }

    private Map<String, Object> promptContext(ItineraryGenerationContext context) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("generationId", context.generationId().toString());
        values.put("tripId", context.tripId().toString());
        values.put("tripTitle", context.tripTitle());
        values.put("promptVersion", context.promptVersion());
        values.put("schemaVersion", context.schemaVersion());
        values.put("destination", context.destination());
        values.put("startDate", context.startDate());
        values.put("endDate", context.endDate());
        values.put("profile", context.profile());
        return values;
    }

    private String loadTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("자동 일정 생성 프롬프트 템플릿을 불러올 수 없습니다.", exception);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("자동 일정 생성 context를 JSON으로 직렬화할 수 없습니다.", exception);
        }
    }
}
