package com.planmate.itinerary.generation;

import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.itinerary.dto.GroundedItineraryDraft;
import org.springframework.stereotype.Component;

@Component
public class GeminiMapsGroundedItineraryDraftGenerator implements ItineraryDraftGenerator {

    private final ItineraryDraftPromptBuilder promptBuilder;
    private final GeminiMapsGroundingClient client;
    private final GroundedItineraryDraftParser parser;

    public GeminiMapsGroundedItineraryDraftGenerator(
            ItineraryDraftPromptBuilder promptBuilder,
            GeminiMapsGroundingClient client,
            GroundedItineraryDraftParser parser
    ) {
        this.promptBuilder = promptBuilder;
        this.client = client;
        this.parser = parser;
    }

    @Override
    public String provider() {
        return AiItineraryProperties.PROVIDER_GEMINI_MAPS_GROUNDING;
    }

    @Override
    public GroundedItineraryDraft generate(ItineraryGenerationContext context) {
        String prompt = promptBuilder.build(context);
        String responseText = client.generateJson(prompt, context.destination().location());
        return parser.parse(responseText);
    }
}
