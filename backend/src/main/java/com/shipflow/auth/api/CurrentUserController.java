package com.shipflow.auth.api;

import com.shipflow.auth.application.AuthApplicationService;
import com.shipflow.auth.api.model.CurrentUserResponse;
import com.shipflow.common.api.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Profile("!test")
public class CurrentUserController {
    private final AuthApplicationService service;
    public CurrentUserController(AuthApplicationService service) { this.service = service; }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(CurrentUserResponse.from(service.currentUser(jwt))));
    }
}
