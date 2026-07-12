package com.planmate.itinerary.service;

import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.itinerary.dto.GroundedItineraryDraft;
import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.dto.ItineraryGenerationDetailResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.generation.ItineraryDraftGenerationException;
import com.planmate.itinerary.generation.ItineraryDraftGenerationFailureCode;
import com.planmate.itinerary.generation.ItineraryDraftGenerator;
import com.planmate.itinerary.generation.ItineraryDraftGeneratorRegistry;
import com.planmate.itinerary.generation.ItineraryDraftPromptBuilder;
import com.planmate.itinerary.generation.ItineraryGenerationContext;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StopWatch;

@Service
public class ItineraryGenerationService {

    private final ItineraryGenerationPersistenceService persistenceService;
    private final AiItineraryProperties aiItineraryProperties;
    private final ItineraryDraftGeneratorRegistry generatorRegistry;

    public ItineraryGenerationService(
            ItineraryGenerationPersistenceService persistenceService,
            AiItineraryProperties aiItineraryProperties,
            ItineraryDraftGeneratorRegistry generatorRegistry
    ) {
        this.persistenceService = persistenceService;
        this.aiItineraryProperties = aiItineraryProperties;
        this.generatorRegistry = generatorRegistry;
    }

    public ItineraryGenerationCreateResponse create(Long userId, Long tripId) {
        return create(userId, tripId, false);
    }

    public ItineraryGenerationCreateResponse create(Long userId, Long tripId, boolean forceRegenerate) {
        ItineraryGenerationEntity generation = persistenceService.createGenerationRequest(
                userId,
                tripId,
                ItineraryDraftPromptBuilder.PROMPT_VERSION,
                forceRegenerate
        );
        return new ItineraryGenerationCreateResponse(
                generation.getId().toString(),
                generation.getStatus(),
                0
        );
    }

    public void generateItinerary(Long userId, Long tripId, Long generationId) {
        if (!aiItineraryProperties.isEnabled()) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_ITINERARY_DISABLED,
                    false
            );
        }

        ItineraryGenerationContext context = persistenceService.loadGenerationContext(userId, tripId, generationId);
        ItineraryDraftGenerator generator = generatorRegistry.get(aiItineraryProperties.getProvider());
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        GroundedItineraryDraft draft = generator.generate(context);
        stopWatch.stop();
        if (draft == null || !generationId.toString().equals(draft.generationId())) {
            throw new ItineraryDraftGenerationException(
                    ItineraryDraftGenerationFailureCode.AI_RESPONSE_INVALID,
                    "AI 일정 응답의 generationId가 현재 생성 작업과 일치하지 않습니다.",
                    false
            );
        }
        persistenceService.saveValidatedDraftAndComplete(
                userId,
                tripId,
                generationId,
                draft,
                stopWatch.getTotalTimeMillis()
        );
    }

    public ItineraryGenerationDetailResponse getDetail(Long userId, Long tripId, Long generationId) {
        return persistenceService.getDetail(userId, tripId, generationId);
    }

    public Optional<ItineraryGenerationDetailResponse> getLatest(Long userId, Long tripId) {
        return persistenceService.getLatest(userId, tripId);
    }
}
