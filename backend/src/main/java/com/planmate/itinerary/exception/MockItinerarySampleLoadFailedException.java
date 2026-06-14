package com.planmate.itinerary.exception;

import com.planmate.common.exception.PlanMateException;

public class MockItinerarySampleLoadFailedException extends PlanMateException {

    public MockItinerarySampleLoadFailedException(Throwable cause) {
        super(ItineraryErrorCode.MOCK_SAMPLE_LOAD_FAILED, cause);
    }

}
