package com.planmate.itinerary.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.planmate.itinerary.config.AiItineraryProperties;
import com.planmate.itinerary.dto.GroundedItineraryDraft;
import com.planmate.itinerary.dto.ItineraryDraftDay;
import com.planmate.itinerary.dto.ItineraryDraftItem;
import com.planmate.itinerary.dto.ItineraryGenerationCreateResponse;
import com.planmate.itinerary.entity.ItineraryGenerationEntity;
import com.planmate.itinerary.entity.ItineraryGenerationStatus;
import com.planmate.itinerary.generation.ItineraryDraftGenerator;
import com.planmate.itinerary.generation.ItineraryDraftGenerationException;
import com.planmate.itinerary.generation.ItineraryDraftGenerationFailureCode;
import com.planmate.itinerary.generation.ItineraryDraftGeneratorRegistry;
import com.planmate.itinerary.generation.ItineraryDraftPromptBuilder;
import com.planmate.itinerary.generation.ItineraryGenerationContext;
import com.planmate.place.dto.GeoPoint;
import com.planmate.trip.entity.TripEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ItineraryGenerationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ItineraryGenerationPersistenceService persistenceService;

    @Mock
    private ItineraryDraftGeneratorRegistry generatorRegistry;

    @Mock
    private ItineraryDraftGenerator generator;

    private AiItineraryProperties properties;
    private ItineraryGenerationService service;

    @BeforeEach
    void setUp() {
        properties = new AiItineraryProperties();
        properties.setProvider(AiItineraryProperties.PROVIDER_GEMINI_MAPS_GROUNDING);
        service = new ItineraryGenerationService(persistenceService, properties, generatorRegistry);
    }

    @Test
    void createOnlyCreatesGenerationRequestAndDoesNotGenerateInHttpRequest() {
        ItineraryGenerationEntity generation = generation(123L, trip(45L));
        given(persistenceService.createGenerationRequest(7L, 45L, ItineraryDraftPromptBuilder.PROMPT_VERSION, false))
                .willReturn(generation);

        ItineraryGenerationCreateResponse response = service.create(7L, 45L);

        assertThat(response.generationId()).isEqualTo("123");
        assertThat(response.status()).isEqualTo(ItineraryGenerationStatus.CREATED);
        assertThat(response.candidateCount()).isZero();
        verify(persistenceService).createGenerationRequest(7L, 45L, ItineraryDraftPromptBuilder.PROMPT_VERSION, false);
        verify(persistenceService, never()).saveValidatedDraftAndComplete(any(), any(), any(), any(), any(Long.class));
        verifyNoMoreInteractions(persistenceService);
    }

    @Test
    void createPassesForceRegenerateOption() {
        ItineraryGenerationEntity generation = generation(123L, trip(45L));
        given(persistenceService.createGenerationRequest(7L, 45L, ItineraryDraftPromptBuilder.PROMPT_VERSION, true))
                .willReturn(generation);

        service.create(7L, 45L, true);

        verify(persistenceService).createGenerationRequest(7L, 45L, ItineraryDraftPromptBuilder.PROMPT_VERSION, true);
    }

    @Test
    void generateItineraryCallsProviderAndPersistsValidatedDraft() {
        ItineraryGenerationContext context = context();
        GroundedItineraryDraft draft = draft("123");
        given(persistenceService.loadGenerationContext(7L, 45L, 123L)).willReturn(context);
        given(generatorRegistry.get(AiItineraryProperties.PROVIDER_GEMINI_MAPS_GROUNDING)).willReturn(generator);
        given(generator.generate(context)).willReturn(draft);

        service.generateItinerary(7L, 45L, 123L);

        verify(generator).generate(context);
        verify(persistenceService).saveValidatedDraftAndComplete(eq(7L), eq(45L), eq(123L), eq(draft), any(Long.class));
    }

    @Test
    void generateItineraryFailsWhenAiItineraryIsDisabled() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> service.generateItinerary(7L, 45L, 123L))
                .isInstanceOfSatisfying(ItineraryDraftGenerationException.class, exception ->
                        assertThat(exception.failureCode())
                                .isEqualTo(ItineraryDraftGenerationFailureCode.AI_ITINERARY_DISABLED));
        verifyNoMoreInteractions(persistenceService);
        verifyNoMoreInteractions(generatorRegistry);
    }

    private ItineraryGenerationEntity generation(Long generationId, TripEntity trip) {
        ItineraryGenerationEntity generation = ItineraryGenerationEntity.create(
                trip,
                ItineraryDraftPromptBuilder.PROMPT_VERSION,
                AiItineraryProperties.PROVIDER_GEMINI_MAPS_GROUNDING,
                "gemini-3.5-flash",
                "grounded-itinerary-draft-v1",
                "fingerprint",
                NOW
        );
        ReflectionTestUtils.setField(generation, "id", generationId);
        return generation;
    }

    private GroundedItineraryDraft draft(String generationId) {
        return new GroundedItineraryDraft(
                generationId,
                List.of(new ItineraryDraftDay(1, List.of(new ItineraryDraftItem(1, "place-1", "09:00", 120))))
        );
    }

    private ItineraryGenerationContext context() {
        return new ItineraryGenerationContext(
                123L,
                45L,
                "Kyoto trip",
                ItineraryDraftPromptBuilder.PROMPT_VERSION,
                "grounded-itinerary-draft-v1",
                "fingerprint",
                new ItineraryGenerationContext.Destination(
                        "place-kyoto",
                        "Kyoto",
                        "Kyoto, Japan",
                        new GeoPoint(35.0, 135.0),
                        null,
                        List.of("locality"),
                        "locality"
                ),
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 1),
                null
        );
    }

    private TripEntity trip(Long tripId) {
        TripEntity trip = TripEntity.create(
                "Kyoto trip",
                "Kyoto",
                "place-kyoto",
                "Kyoto, Japan",
                35.0,
                135.0,
                null,
                null,
                null,
                null,
                List.of("locality"),
                "locality",
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 3),
                null,
                NOW
        );
        ReflectionTestUtils.setField(trip, "id", tripId);
        return trip;
    }
}
