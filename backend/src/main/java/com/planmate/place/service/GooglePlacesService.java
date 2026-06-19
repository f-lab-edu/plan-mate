package com.planmate.place.service;

import com.planmate.place.dto.PlaceAutocompleteItemResponse;
import com.planmate.place.dto.PlaceAutocompleteResponse;
import com.planmate.place.exception.InvalidPlaceIdException;
import com.planmate.place.exception.PlaceProviderUnavailableException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
public class GooglePlacesService {

    private static final String BASE_URL = "https://places.googleapis.com/v1";
    private static final String API_KEY_HEADER = "X-Goog-Api-Key";
    private static final String FIELD_MASK_HEADER = "X-Goog-FieldMask";
    private static final String AUTOCOMPLETE_FIELD_MASK = String.join(",",
            "suggestions.placePrediction.placeId",
            "suggestions.placePrediction.text.text",
            "suggestions.placePrediction.structuredFormat.mainText.text",
            "suggestions.placePrediction.structuredFormat.secondaryText.text",
            "suggestions.placePrediction.types"
    );
    private static final String DETAILS_ID_FIELD_MASK = "id";

    private final RestClient restClient;
    private final String apiKey;

    public GooglePlacesService(
            RestClient.Builder restClientBuilder,
            @Value("${app.google.places.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl(BASE_URL)
                .build();
        this.apiKey = apiKey;
    }

    public PlaceAutocompleteResponse autocomplete(String query, String sessionToken, String languageCode) {
        assertApiKeyConfigured();

        try {
            GoogleAutocompleteResponse response = restClient.post()
                    .uri("/places:autocomplete")
                    .headers(headers -> applyGoogleHeaders(headers, AUTOCOMPLETE_FIELD_MASK))
                    .body(autocompleteRequestBody(query, sessionToken, languageCode))
                    .retrieve()
                    .body(GoogleAutocompleteResponse.class);

            return normalizeAutocomplete(response);
        } catch (RestClientException exception) {
            throw new PlaceProviderUnavailableException(exception);
        }
    }

    public void validatePlaceId(String placeId, String sessionToken) {
        assertApiKeyConfigured();

        try {
            GooglePlaceDetailsResponse response = restClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/places/{placeId}");
                        if (StringUtils.hasText(sessionToken)) {
                            builder.queryParam("sessionToken", sessionToken.trim());
                        }
                        return builder.build(placeId);
                    })
                    .headers(headers -> applyGoogleHeaders(headers, DETAILS_ID_FIELD_MASK))
                    .retrieve()
                    .body(GooglePlaceDetailsResponse.class);

            if (response == null || !placeId.equals(response.id())) {
                throw new InvalidPlaceIdException();
            }
        } catch (RestClientResponseException exception) {
            if (isInvalidPlaceIdResponse(exception.getStatusCode())) {
                throw new InvalidPlaceIdException();
            }
            throw new PlaceProviderUnavailableException(exception);
        } catch (RestClientException exception) {
            throw new PlaceProviderUnavailableException(exception);
        }
    }

    private void assertApiKeyConfigured() {
        if (!StringUtils.hasText(apiKey)) {
            throw new PlaceProviderUnavailableException();
        }
    }

    private void applyGoogleHeaders(HttpHeaders headers, String fieldMask) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(API_KEY_HEADER, apiKey);
        headers.set(FIELD_MASK_HEADER, fieldMask);
    }

    private Map<String, Object> autocompleteRequestBody(String query, String sessionToken, String languageCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("input", query.trim());
        body.put("sessionToken", sessionToken.trim());
        if (StringUtils.hasText(languageCode)) {
            body.put("languageCode", languageCode.trim());
        }
        return body;
    }

    private PlaceAutocompleteResponse normalizeAutocomplete(GoogleAutocompleteResponse response) {
        if (response == null || response.suggestions() == null) {
            return new PlaceAutocompleteResponse(List.of());
        }

        List<PlaceAutocompleteItemResponse> items = response.suggestions()
                .stream()
                .map(GoogleSuggestion::placePrediction)
                .filter(Objects::nonNull)
                .filter(prediction -> StringUtils.hasText(prediction.placeId()))
                .map(this::toItemResponse)
                .toList();

        return new PlaceAutocompleteResponse(items);
    }

    private PlaceAutocompleteItemResponse toItemResponse(GooglePlacePrediction prediction) {
        String displayText = textValue(prediction.text());
        String mainText = textValue(prediction.structuredFormat() == null ? null : prediction.structuredFormat().mainText());
        String secondaryText = textValue(prediction.structuredFormat() == null ? null : prediction.structuredFormat().secondaryText());

        if (!StringUtils.hasText(mainText)) {
            mainText = displayText;
        }
        if (!StringUtils.hasText(displayText)) {
            displayText = StringUtils.hasText(secondaryText) ? mainText + ", " + secondaryText : mainText;
        }

        return new PlaceAutocompleteItemResponse(
                prediction.placeId(),
                mainText,
                secondaryText,
                displayText,
                prediction.types() == null ? List.of() : List.copyOf(prediction.types())
        );
    }

    private String textValue(GoogleText text) {
        return text == null || text.text() == null ? "" : text.text();
    }

    private boolean isInvalidPlaceIdResponse(HttpStatusCode statusCode) {
        return statusCode.value() == 400 || statusCode.value() == 404;
    }

    private record GoogleAutocompleteResponse(
            List<GoogleSuggestion> suggestions
    ) {
    }

    private record GoogleSuggestion(
            GooglePlacePrediction placePrediction
    ) {
    }

    private record GooglePlacePrediction(
            String placeId,
            GoogleText text,
            GoogleStructuredFormat structuredFormat,
            List<String> types
    ) {
    }

    private record GoogleStructuredFormat(
            GoogleText mainText,
            GoogleText secondaryText
    ) {
    }

    private record GoogleText(
            String text
    ) {
    }

    private record GooglePlaceDetailsResponse(
            String id
    ) {
    }

}
