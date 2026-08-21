package com.shipflow.user;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.user.api.UserController;
import com.shipflow.user.application.UserApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, UserControllerWebMvcTest.Config.class})
class UserControllerWebMvcTest {
    @Autowired MockMvc mvc; @MockBean UserApplicationService service;
    @TestConfiguration static class Config { @Bean JwtDecoder jwtDecoder(){return token->{throw new org.springframework.security.oauth2.jwt.BadJwtException("test");};} }
    @Test void anonymousIsUnauthorized() throws Exception { mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized()); }
    @Test void missingTenantAuthorityIsForbidden() throws Exception { mvc.perform(get("/api/v1/users").with(jwt())).andExpect(status().isForbidden()); }
    @Test void tenantUserWithPermissionCanReachController() throws Exception { mvc.perform(get("/api/v1/users").with(jwt().jwt(j->j.subject("2").claim("tenant_id","1")).authorities(new SimpleGrantedAuthority("scope:TENANT"),new SimpleGrantedAuthority("user:manage")))).andExpect(status().isOk()); }
}
