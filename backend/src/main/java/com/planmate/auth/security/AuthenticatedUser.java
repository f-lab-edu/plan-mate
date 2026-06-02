package com.planmate.auth.security;

import com.planmate.user.domain.UserAccount;
import com.planmate.user.domain.UserRole;
import com.planmate.user.domain.UserStatus;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedUser(
        Long id,
        String email,
        String nickname,
        UserRole role,
        UserStatus status
) {

    public static AuthenticatedUser from(UserAccount userAccount) {
        return new AuthenticatedUser(
                userAccount.id(),
                userAccount.email(),
                userAccount.nickname(),
                userAccount.role(),
                userAccount.status()
        );
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

}

