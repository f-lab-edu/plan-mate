package com.planmate.itinerary.generation;

import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.itinerary.dto.GroundedItineraryDraft;
import org.springframework.stereotype.Component;

@Component
public class ManualItineraryDraftGenerator implements ItineraryDraftGenerator {

    @Override
    public String provider() {
        return AiItineraryProperties.PROVIDER_MANUAL;
    }

    @Override
    public GroundedItineraryDraft generate(ItineraryGenerationContext context) {
        throw new ItineraryDraftGenerationException(
                ItineraryDraftGenerationFailureCode.AI_ITINERARY_DISABLED,
                "수동 handoff provider는 자동 일정 JSON을 생성하지 않습니다.",
                false
        );
    }
}
