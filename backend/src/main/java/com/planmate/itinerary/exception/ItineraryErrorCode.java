package com.planmate.itinerary.exception;

import com.planmate.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ItineraryErrorCode implements ErrorCode {

    MOCK_SAMPLE_NOT_FOUND(HttpStatus.BAD_REQUEST, "MOCK_SAMPLE_NOT_FOUND", "Mock itinerary sample not found."),
    MOCK_SAMPLE_LOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "MOCK_SAMPLE_LOAD_FAILED", "Mock itinerary samples could not be loaded.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ItineraryErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

}
