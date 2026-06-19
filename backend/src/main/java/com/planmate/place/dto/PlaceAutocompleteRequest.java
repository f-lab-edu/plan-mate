package com.planmate.place.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceAutocompleteRequest(
        @NotBlank
        @Size(min = 2, max = 120)
        String query,

        @NotBlank
        @Size(max = 100)
        String sessionToken,

        @Size(max = 10)
        String languageCode
) {
}
