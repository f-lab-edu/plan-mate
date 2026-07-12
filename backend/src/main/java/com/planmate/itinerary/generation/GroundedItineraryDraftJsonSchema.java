package com.planmate.itinerary.generation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GroundedItineraryDraftJsonSchema {

    public Map<String, Object> toJsonSchema() {
        Map<String, Object> item = object(
                Map.of(
                        "sequence", integer("일정 항목 순서입니다. 각 day 안에서 1부터 시작합니다."),
                        "placeId", string("Google Places placeId입니다."),
                        "startTime", string("HH:mm 형식의 방문 시작 시각입니다."),
                        "durationMinutes", integer("방문 체류 시간(분)입니다.")
                ),
                List.of("sequence", "placeId", "startTime", "durationMinutes")
        );

        Map<String, Object> day = object(
                Map.of(
                        "day", integer("여행 일차입니다. 1부터 시작합니다."),
                        "items", array(item)
                ),
                List.of("day", "items")
        );

        return object(
                Map.of(
                        "generationId", string("일정 생성 작업 ID입니다."),
                        "days", array(day)
                ),
                List.of("generationId", "days")
        );
    }

    private Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private Map<String, Object> array(Map<String, Object> itemSchema) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "array");
        schema.put("items", itemSchema);
        return schema;
    }

    private Map<String, Object> string(String description) {
        return Map.of(
                "type", "string",
                "description", description
        );
    }

    private Map<String, Object> integer(String description) {
        return Map.of(
                "type", "integer",
                "description", description
        );
    }
}
