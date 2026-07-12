package com.planmate.itinerary.service;

import com.planmate.common.exception.PlanMateException;
import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.itinerary.config.ItineraryGenerationWorkerProperties;
import com.planmate.itinerary.generation.ItineraryDraftGenerationException;
import com.planmate.itinerary.generation.ItineraryDraftGenerationFailureCode;
import com.planmate.itinerary.messaging.ItineraryGenerationRequestedMessage;
import com.planmate.itinerary.metrics.ItineraryGenerationWorkerMetrics;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ItineraryGenerationWorkerService {

    private static final Logger log = LoggerFactory.getLogger(ItineraryGenerationWorkerService.class);

    private final ItineraryGenerationPersistenceService persistenceService;
    private final ItineraryGenerationService generationService;
    private final ItineraryGenerationWorkerProperties properties;
    private final AiItineraryProperties aiItineraryProperties;
    private final ItineraryGenerationWorkerMetrics metrics;

    public ItineraryGenerationWorkerService(
            ItineraryGenerationPersistenceService persistenceService,
            ItineraryGenerationService generationService,
            ItineraryGenerationWorkerProperties properties,
            AiItineraryProperties aiItineraryProperties,
            ItineraryGenerationWorkerMetrics metrics
    ) {
        this.persistenceService = persistenceService;
        this.generationService = generationService;
        this.properties = properties;
        this.aiItineraryProperties = aiItineraryProperties;
        this.metrics = metrics;
    }

    public void process(ItineraryGenerationRequestedMessage message) {
        Timer.Sample sample = metrics.start();
        String result = ItineraryGenerationWorkerMetrics.RESULT_FAILED;
        try {
            validate(message);
            boolean shouldProcess = persistenceService.markPlanningIfCreated(
                    message.userId(),
                    message.tripId(),
                    message.generationId()
            );
            if (!shouldProcess) {
                result = ItineraryGenerationWorkerMetrics.RESULT_SKIPPED;
                return;
            }

            generateItineraryWithRetry(message);
            result = ItineraryGenerationWorkerMetrics.RESULT_SUCCESS;
        } finally {
            metrics.recordProcessed(result, sample);
        }
    }

    private void generateItineraryWithRetry(ItineraryGenerationRequestedMessage message) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                generationService.generateItinerary(message.userId(), message.tripId(), message.generationId());
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt < properties.getMaxAttempts() && isRetryable(exception)) {
                    metrics.recordRetry();
                } else {
                    break;
                }
            }
        }

        log.warn(
                "AI 일정 생성 실패. provider={}, model={}, generationId={}, tripId={}, failureCode={}",
                aiItineraryProperties.getProvider(),
                aiItineraryProperties.getModel(),
                message.generationId(),
                message.tripId(),
                failureCode(lastFailure)
        );
        persistenceService.markFailed(message.generationId(), failureCode(lastFailure), userMessage(lastFailure));
        throw lastFailure;
    }

    private void validate(ItineraryGenerationRequestedMessage message) {
        if (message.generationId() == null || message.tripId() == null || message.userId() == null) {
            throw new IllegalArgumentException("itinerary generation message must include generationId, tripId, and userId");
        }
    }

    private boolean isRetryable(RuntimeException exception) {
        return exception instanceof ItineraryDraftGenerationException generationException
                && generationException.retryable();
    }

    private String failureCode(RuntimeException exception) {
        if (exception instanceof ItineraryDraftGenerationException generationException) {
            return generationException.failureCode().name();
        }
        if (exception instanceof PlanMateException planMateException) {
            return planMateException.code();
        }
        if (exception == null) {
            return ItineraryDraftGenerationFailureCode.AI_PROVIDER_UNAVAILABLE.name();
        }
        return exception.getClass().getSimpleName();
    }

    private String userMessage(RuntimeException exception) {
        if (exception instanceof ItineraryDraftGenerationException generationException) {
            return generationException.getMessage();
        }
        if (exception instanceof PlanMateException planMateException) {
            return planMateException.getMessage();
        }
        return "자동 일정 생성 중 알 수 없는 문제가 발생했습니다.";
    }
}
