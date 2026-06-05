package com.planmate.auth.email;

import com.planmate.user.entity.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LoggingAuthEmailSender implements AuthEmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthEmailSender.class);

    private final String frontendBaseUrl;

    public LoggingAuthEmailSender(@Value("${app.frontend.base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void sendSignupVerification(UserEntity user, String rawToken) {
        log.info("Signup verification email prepared. userId={}, url={}/auth/email-verification?token={}",
                user.getId(), frontendBaseUrl, rawToken);
    }

    @Override
    public void sendLoginIdGuide(UserEntity user, String loginId) {
        log.info("Login id guide email prepared. userId={}, loginId={}", user.getId(), loginId);
    }

    @Override
    public void sendPasswordReset(UserEntity user, String rawToken) {
        log.info("Password reset email prepared. userId={}, url={}/auth/password-reset?token={}",
                user.getId(), frontendBaseUrl, rawToken);
    }

    @Override
    public void sendSocialAccountGuide(String email) {
        log.info("Social account guide email prepared. email={}", email);
    }

}
