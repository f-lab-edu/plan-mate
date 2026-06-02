package com.planmate.auth.service;

import com.planmate.auth.dto.LoginRequest;
import com.planmate.auth.dto.LoginResponse;
import com.planmate.auth.exception.InactiveUserException;
import com.planmate.auth.exception.InvalidCredentialsException;
import com.planmate.auth.token.IssuedToken;
import com.planmate.auth.token.JwtTokenProvider;
import com.planmate.user.domain.UserAccount;
import com.planmate.user.entity.UserEntity;
import com.planmate.user.repository.UserRepository;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserEntity user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        UserAccount userAccount = user.toAccount();
        if (!userAccount.isActive()) {
            throw new InactiveUserException();
        }

        IssuedToken issuedToken = jwtTokenProvider.issueAccessToken(userAccount);
        return LoginResponse.of(issuedToken, userAccount);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

}

