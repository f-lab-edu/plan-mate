package com.planmate.auth.controller;

import com.planmate.auth.dto.CurrentUserResponse;
import com.planmate.auth.dto.AuthStatusResponse;
import com.planmate.auth.dto.LoginRequest;
import com.planmate.auth.dto.LoginResponse;
import com.planmate.auth.dto.SignupRequest;
import com.planmate.auth.dto.SignupResponse;
import com.planmate.auth.security.AuthenticatedUser;
import com.planmate.auth.service.LoginService;
import com.planmate.auth.service.SignupService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final SignupService signupService;
    private final LoginService loginService;

    public AuthController(SignupService signupService, LoginService loginService) {
        this.signupService = signupService;
        this.loginService = loginService;
    }

    @GetMapping("/status")
    public AuthStatusResponse status(Authentication authentication) {
        return new AuthStatusResponse(authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser);
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = signupService.signup(request);
        return ResponseEntity
                .created(URI.create("/api/users/" + response.id()))
                .body(response);
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return CurrentUserResponse.from(user);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Stateless JWT logout is completed by deleting the token on the client.
    }

}
