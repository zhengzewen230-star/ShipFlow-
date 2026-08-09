package com.shipflow.rbac.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.rbac.api.model.RolePermissionBindingRequest;
import com.shipflow.rbac.application.RbacApplicationService;
import com.shipflow.rbac.domain.model.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class RbacController {
    private final RbacApplicationService service;
    public RbacController(RbacApplicationService service){this.service=service;}
    private Long tenant(Jwt jwt){return Long.valueOf(jwt.getClaimAsString("tenant_id"));}
    private Long user(Jwt jwt){return Long.valueOf(jwt.getSubject());}
    @GetMapping("/roles") public ApiResponse<List<Role>> roles(@RequestParam(required=false)String status,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.list(tenant(jwt),status));}
    @GetMapping("/roles/{id}") public ApiResponse<Role> role(@PathVariable Long id,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.get(tenant(jwt),id));}
    @GetMapping("/permissions") public ApiResponse<List<Permission>> permissions(){return ApiResponse.success(service.permissions());}
    @PutMapping("/roles/{id}/permissions") public ApiResponse<Role> replace(@PathVariable Long id,@Valid @RequestBody RolePermissionBindingRequest r,@RequestHeader(value="X-Request-Id",required=false)String req,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.replace(tenant(jwt),id,r,user(jwt),req));}
}
