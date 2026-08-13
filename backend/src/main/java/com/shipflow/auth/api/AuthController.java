package com.shipflow.auth.api;

import com.shipflow.auth.application.AuthApplicationService;
import com.shipflow.auth.application.model.LoginCommand;
import com.shipflow.auth.api.model.AuthLoginRequest;
import com.shipflow.auth.api.model.AuthTokenResponse;
import com.shipflow.common.api.ApiResponse;
import com.shipflow.security.refresh.RefreshToken;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@RestController
@RequestMapping("/api/v1/auth")
@Profile("!test")
public class AuthController {

    private final AuthApplicationService service;
    private final AuthCookieProperties cookies;
    private final CsrfTokenRepository csrfTokenRepository;

    public AuthController(AuthApplicationService service, AuthCookieProperties cookies,
                          CsrfTokenRepository csrfTokenRepository) {
        this.service = service;
        this.cookies = cookies;
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Void> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(token, request, response);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> login(@Valid @RequestBody AuthLoginRequest request) {
        var result = service.login(new LoginCommand(request.username(), request.password(), request.tenantCode()));
        return tokenResponse(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthTokenResponse>> refresh(HttpServletRequest request) {
        var result = service.refresh(readCookie(request, cookies.getRefreshName()));
        return tokenResponse(result);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        service.logout(readCookie(request, cookies.getRefreshName()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookies.clearRefreshCookie().toString())
                .header(HttpHeaders.SET_COOKIE, cookies.clearXsrfCookie().toString())
                .body(ApiResponse.success(null));
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        for (var cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private ResponseEntity<ApiResponse<AuthTokenResponse>> tokenResponse(
            com.shipflow.auth.application.AuthSessionResult result) {
        ResponseCookie cookie = cookies.refreshCookie(result.refreshToken());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success(AuthTokenResponse.from(result.loginResult())));
    }
}
