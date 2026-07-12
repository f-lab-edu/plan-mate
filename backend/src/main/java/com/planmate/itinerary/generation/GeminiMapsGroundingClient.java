package com.planmate.itinerary.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.place.dto.GeoPoint;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class GeminiMapsGroundingClient {

    private static final String API_KEY_HEADER = "x-goog-api-key";

    private final RestClient restClient;
    private final AiItineraryProperties properties;
    private final GroundedItineraryDraftJsonSchema jsonSchema;

    public GeminiMapsGroundingClient(
            RestClient.Builder restClientBuilder,
            AiItineraryProperties properties,
            GroundedItineraryDraftJsonSchema jsonSchema
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        this.restClient = restClientBuilder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
        this.jsonSchema = jsonSchema;
    }

    public String generateJson(String prompt, GeoPoint location) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_AUTHENTICATION_FAILED,
                    false
            );
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/interactions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(API_KEY_HEADER, properties.getApiKey())
                    .body(requestBody(prompt, location))
                    .retrieve()
                    .body(JsonNode.class);
            return outputText(response);
        } catch (RestClientResponseException exception) {
            throw mapResponseException(exception);
        } catch (ResourceAccessException exception) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_TIMEOUT,
                    true,
                    exception
            );
        } catch (RestClientException exception) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_PROVIDER_UNAVAILABLE,
                    true,
                    exception
            );
        }
    }

    private Map<String, Object> requestBody(String prompt, GeoPoint location) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getModel());
        body.put("store", false);
        body.put("input", prompt);
        body.put("tools", List.of(googleMapsTool(location)));
        body.put("response_format", Map.of(
                "type", "text",
                "mime_type", "application/json",
                "schema", jsonSchema.toJsonSchema()
        ));
        body.put("generation_config", Map.of(
                "temperature", properties.getTemperature(),
                "thinking_level", "low"
        ));
        return body;
    }

    private Map<String, Object> googleMapsTool(GeoPoint location) {
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "google_maps");
        if (location != null) {
            tool.put("latitude", location.latitude());
            tool.put("longitude", location.longitude());
        }
        return tool;
    }

    private String outputText(JsonNode response) {
        if (response == null) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_RESPONSE_EMPTY,
                    false
            );
        }
        JsonNode outputText = response.get("output_text");
        if (outputText != null && outputText.isTextual() && StringUtils.hasText(outputText.asText())) {
            return outputText.asText();
        }

        List<String> textBlocks = new ArrayList<>();
        JsonNode steps = response.get("steps");
        if (steps != null && steps.isArray()) {
            for (JsonNode step : steps) {
                collectModelOutputText(step, textBlocks);
            }
        }
        String joined = String.join("", textBlocks).trim();
        if (!StringUtils.hasText(joined)) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_RESPONSE_EMPTY,
                    false
            );
        }
        return joined;
    }

    private void collectModelOutputText(JsonNode step, List<String> textBlocks) {
        JsonNode type = step.get("type");
        if (type == null || !"model_output".equals(type.asText())) {
            return;
        }
        JsonNode content = step.get("content");
        if (content == null || !content.isArray()) {
            return;
        }
        for (JsonNode block : content) {
            JsonNode blockType = block.get("type");
            JsonNode text = block.get("text");
            if (blockType != null && "text".equals(blockType.asText()) && text != null && text.isTextual()) {
                textBlocks.add(text.asText());
            }
        }
    }

    private ItineraryDraftGenerationException mapResponseException(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401 || status == 403) {
            return new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_AUTHENTICATION_FAILED,
                    false,
                    exception
            );
        }
        if (status == 429) {
            return new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_QUOTA_EXCEEDED,
                    false,
                    exception
            );
        }
        if (status == 408 || status == 504) {
            return new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_TIMEOUT,
                    true,
                    exception
            );
        }
        if (status >= 500) {
            return new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_PROVIDER_UNAVAILABLE,
                    true,
                    exception
            );
        }
        return new ItineraryDraftGenerationException(
                ItineraryDraftGenerationFailureCode.AI_PROVIDER_UNAVAILABLE,
                false,
                exception
        );
    }
}
