package com.planmate.itinerary.generation;

public class ItineraryDraftGenerationException extends RuntimeException {

    private final ItineraryDraftGenerationFailureCode failureCode;
    private final boolean retryable;

    public ItineraryDraftGenerationException(ItineraryDraftGenerationFailureCode failureCode, boolean retryable) {
        super(failureCode.userMessage());
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public ItineraryDraftGenerationException(
            ItineraryDraftGenerationFailureCode failureCode,
            boolean retryable,
            Throwable cause
    ) {
        super(failureCode.userMessage(), cause);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public ItineraryDraftGenerationException(
            ItineraryDraftGenerationFailureCode failureCode,
            String userMessage,
            boolean retryable
    ) {
        super(userMessage);
        this.failureCode = failureCode;
        this.retryable = retryable;
    }

    public ItineraryDraftGenerationFailureCode failureCode() {
        return failureCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
