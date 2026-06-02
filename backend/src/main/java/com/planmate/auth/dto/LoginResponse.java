package com.planmate.auth.dto;

import com.planmate.auth.token.IssuedToken;
import com.planmate.user.domain.UserAccount;
import java.time.Instant;

public record LoginResponse(
        String tokenType,
        String accessToken,
        Instant expiresAt,
        LoginUserResponse user
) {

    public static LoginResponse of(IssuedToken issuedToken, UserAccount userAccount) {
        return new LoginResponse(
                "Bearer",
                issuedToken.accessToken(),
                issuedToken.expiresAt(),
                LoginUserResponse.from(userAccount)
        );
    }

    public record LoginUserResponse(
            Long id,
            String email,
            String nickname
    ) {

        private static LoginUserResponse from(UserAccount userAccount) {
            return new LoginUserResponse(
                    userAccount.id(),
                    userAccount.email(),
                    userAccount.nickname()
            );
        }

    }

}

