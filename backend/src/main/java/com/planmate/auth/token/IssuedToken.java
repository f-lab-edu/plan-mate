package com.planmate.auth.token;

import java.time.Instant;

public record IssuedToken(
        String accessToken,
        Instant expiresAt
) {
}

