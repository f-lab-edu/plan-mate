package com.planmate.auth.dto;

public record GuidanceResponse(String message) {

    public static GuidanceResponse sentIfPossible() {
        return new GuidanceResponse("입력한 이메일로 안내가 가능한 경우 메일을 발송했습니다.");
    }

    public static GuidanceResponse verificationSentIfPossible() {
        return new GuidanceResponse("인증 메일 발송이 가능한 경우 메일을 발송했습니다.");
    }

}
