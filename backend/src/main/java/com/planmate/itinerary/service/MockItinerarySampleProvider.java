package com.planmate.itinerary.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.planmate.itinerary.exception.MockItinerarySampleLoadFailedException;
import com.planmate.itinerary.exception.MockItinerarySampleNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class MockItinerarySampleProvider {

    private static final String SAMPLE_RESOURCE_PATH = "mock/itinerary-samples.json";

    private final Map<String, MockItinerarySample> samplesById;

    public MockItinerarySampleProvider(ObjectMapper objectMapper) {
        try (InputStream inputStream = new ClassPathResource(SAMPLE_RESOURCE_PATH).getInputStream()) {
            List<MockItinerarySample> samples = objectMapper.readValue(
                    inputStream,
                    new TypeReference<List<MockItinerarySample>>() {
                    }
            );
            this.samplesById = samples.stream()
                    .collect(Collectors.toUnmodifiableMap(MockItinerarySample::mockSampleId, Function.identity()));
        } catch (IOException exception) {
            throw new MockItinerarySampleLoadFailedException(exception);
        }
    }

    public MockItinerarySample getById(String mockSampleId) {
        MockItinerarySample sample = samplesById.get(mockSampleId);
        if (sample == null) {
            throw new MockItinerarySampleNotFoundException(mockSampleId);
        }
        return sample;
    }

}
