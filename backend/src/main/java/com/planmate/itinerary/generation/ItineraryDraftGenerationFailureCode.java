package com.planmate.itinerary.generation;

public enum ItineraryDraftGenerationFailureCode {
    AI_ITINERARY_DISABLED("AI 일정 생성 기능이 현재 비활성화되어 있습니다."),
    AI_PROVIDER_NOT_FOUND("AI 일정 생성 provider 설정을 찾을 수 없습니다."),
    AI_AUTHENTICATION_FAILED("AI 일정 생성 인증에 실패했습니다."),
    AI_QUOTA_EXCEEDED("AI 일정 생성 사용량 한도를 초과했습니다."),
    AI_TIMEOUT("AI 일정 생성 요청 시간이 초과되었습니다."),
    AI_PROVIDER_UNAVAILABLE("AI 일정 생성 서비스가 일시적으로 응답하지 않습니다."),
    AI_RESPONSE_EMPTY("AI가 일정 응답을 반환하지 않았습니다."),
    AI_RESPONSE_NOT_JSON("AI 응답을 일정 JSON으로 해석하지 못했습니다."),
    AI_RESPONSE_INVALID("AI 응답의 일정 형식이 올바르지 않습니다."),
    AI_RESPONSE_VALIDATION_FAILED("AI가 생성한 일정이 필수 검증 조건을 만족하지 못했습니다."),
    AI_ITINERARY_SAVE_FAILED("AI 일정을 저장하는 데 실패했습니다.");

    private final String userMessage;

    ItineraryDraftGenerationFailureCode(String userMessage) {
        this.userMessage = userMessage;
    }

    public String userMessage() {
        return userMessage;
    }
}
