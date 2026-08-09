package com.shipflow.rbac;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.rbac.api.RbacController;
import com.shipflow.rbac.application.RbacApplicationService;
import com.shipflow.security.SecurityConfig;
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

@WebMvcTest(RbacController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RbacControllerWebMvcTest.Config.class})
class RbacControllerWebMvcTest {
    @Autowired MockMvc mvc; @MockBean RbacApplicationService service;
    @TestConfiguration static class Config { @Bean JwtDecoder jwtDecoder(){return token->{throw new org.springframework.security.oauth2.jwt.BadJwtException("test");};} }
    @Test void anonymousIsUnauthorized() throws Exception { mvc.perform(get("/api/v1/roles")).andExpect(status().isUnauthorized()); }
    @Test void wrongScopeIsForbidden() throws Exception { mvc.perform(get("/api/v1/roles").with(jwt().authorities(new SimpleGrantedAuthority("role:read")))).andExpect(status().isForbidden()); }
}
