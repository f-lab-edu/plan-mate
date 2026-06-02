package com.planmate.auth.token;

import java.time.Instant;

public record JwtClaims(
        Long userId,
        Instant expiresAt
) {
}

