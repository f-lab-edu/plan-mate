package com.planmate.itinerary.exception;

import com.planmate.common.exception.PlanMateException;

public class MockItinerarySampleNotFoundException extends PlanMateException {

    public MockItinerarySampleNotFoundException(String mockSampleId) {
        super(ItineraryErrorCode.MOCK_SAMPLE_NOT_FOUND, "Mock itinerary sample not found: " + mockSampleId);
    }

}
