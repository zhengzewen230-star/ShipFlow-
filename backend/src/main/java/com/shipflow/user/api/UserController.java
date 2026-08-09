package com.shipflow.user.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.user.api.model.*;
import com.shipflow.user.application.UserApplicationService;
import com.shipflow.user.domain.model.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final UserApplicationService service;
    public UserController(UserApplicationService service){this.service=service;}
    private Long tenant(Jwt jwt){return Long.valueOf(jwt.getClaimAsString("tenant_id"));}
    private Long user(Jwt jwt){return Long.valueOf(jwt.getSubject());}
    @PostMapping public ResponseEntity<ApiResponse<User>> create(@Valid @RequestBody CreateUserRequest r,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="X-Request-Id",required=false)String req,@AuthenticationPrincipal Jwt jwt){return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.create(tenant(jwt),r,key,user(jwt),req)));}
    @GetMapping public ApiResponse<UserPage> page(@RequestParam(defaultValue="1")int page,@RequestParam(defaultValue="20")int pageSize,@RequestParam(required=false)String status,@RequestParam(required=false)String username,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.page(tenant(jwt),status,username,page,pageSize));}
    @GetMapping("/{id}") public ApiResponse<User> get(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.get(tenant(jwt),id));}
    @PutMapping("/{id}") public ApiResponse<User> update(@PathVariable Long id,@Valid @RequestBody UpdateUserRequest r,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="X-Request-Id",required=false)String req,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.update(tenant(jwt),id,r,key,user(jwt),req));}
    @PostMapping("/{id}/status") public ApiResponse<User> status(@PathVariable Long id,@Valid @RequestBody UserStatusChangeRequest r,@RequestHeader(value="X-Request-Id",required=false)String req,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.status(tenant(jwt),id,r,user(jwt),req));}
    @PutMapping("/{id}/roles") public ApiResponse<User> roles(@PathVariable Long id,@Valid @RequestBody UserRoleBindingRequest r,@RequestHeader(value="X-Request-Id",required=false)String req,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.roles(tenant(jwt),id,r,user(jwt),req));}
}
