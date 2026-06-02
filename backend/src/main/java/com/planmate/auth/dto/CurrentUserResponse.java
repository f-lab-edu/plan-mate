package com.planmate.auth.dto;

import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.user.domain.UserRole;
import com.planmate.user.domain.UserStatus;

public record CurrentUserResponse(
        Long id,
        String email,
        String nickname,
        UserRole role,
        UserStatus status
) {

    public static CurrentUserResponse from(AuthenticatedUser user) {
        return new CurrentUserResponse(
                user.id(),
                user.email(),
                user.nickname(),
                user.role(),
                user.status()
        );
    }

}

