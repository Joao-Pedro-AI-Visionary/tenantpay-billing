package com.tenantpay.billing.web;

import com.tenantpay.billing.application.GetMe;
import com.tenantpay.billing.application.Login;
import com.tenantpay.billing.application.SignUpTenant;
import com.tenantpay.billing.web.dto.AuthDtos.AuthResponse;
import com.tenantpay.billing.web.dto.AuthDtos.LoginRequest;
import com.tenantpay.billing.web.dto.AuthDtos.MeResponse;
import com.tenantpay.billing.web.dto.AuthDtos.SignUpRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Auth")
public class AuthController {
    private final SignUpTenant signUp;
    private final Login login;
    private final GetMe getMe;

    public AuthController(SignUpTenant signUp, Login login, GetMe getMe) {
        this.signUp = signUp;
        this.login = login;
        this.getMe = getMe;
    }

    @PostMapping("/api/v1/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse signUp(@Valid @RequestBody SignUpRequest request) {
        return AuthResponse.of(signUp.execute(
                new SignUpTenant.Command(request.tenantName(), request.email(), request.password())));
    }

    @PostMapping("/api/v1/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return AuthResponse.of(login.execute(new Login.Command(request.email(), request.password())));
    }

    @GetMapping("/api/v1/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        CurrentUser user = CurrentUser.of(jwt);
        return MeResponse.of(getMe.execute(user.tenantId(), user.userId()));
    }
}
