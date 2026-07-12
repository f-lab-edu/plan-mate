package com.planmate.itinerary.generation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ItineraryDraftGeneratorRegistry {

    private final Map<String, ItineraryDraftGenerator> generators;

    public ItineraryDraftGeneratorRegistry(List<ItineraryDraftGenerator> generators) {
        this.generators = generators.stream()
                .collect(Collectors.toUnmodifiableMap(ItineraryDraftGenerator::provider, Function.identity()));
    }

    public ItineraryDraftGenerator get(String provider) {
        ItineraryDraftGenerator generator = generators.get(provider);
        if (generator == null) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_PROVIDER_NOT_FOUND,
                    false
            );
        }
        return generator;
    }
}
