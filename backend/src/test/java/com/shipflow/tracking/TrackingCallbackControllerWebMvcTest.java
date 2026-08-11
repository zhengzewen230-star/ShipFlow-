package com.shipflow.tracking;

import com.shipflow.common.exception.GlobalExceptionHandler;
import com.shipflow.security.SecurityConfig;
import com.shipflow.tracking.api.TrackingCallbackController;
import com.shipflow.tracking.api.model.TrackingCallbackResult;
import com.shipflow.tracking.api.model.TrackingEventResult;
import com.shipflow.tracking.application.TrackingCallbackApplicationService;
import com.shipflow.tracking.application.TrackingCallbackException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TrackingCallbackController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class TrackingCallbackControllerWebMvcTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    TrackingCallbackApplicationService service;

    @Test
    void callbackIsCsrfExemptAndReturnsAcceptedBatchResult() throws Exception {
        when(service.receive(eq("MOCK"), eq("1786406400"), eq("signature"), any(), eq("request-1")))
                .thenReturn(new TrackingCallbackResult(1, 0, 0,
                        List.of(TrackingEventResult.accepted("EVT-1"))));

        mvc.perform(post("/api/v1/integrations/logistics/MOCK/tracking-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Provider-Timestamp", "1786406400")
                        .header("X-Provider-Signature", "signature")
                        .header("X-Request-Id", "request-1")
                        .content("{\"events\":[]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accepted").value(1))
                .andExpect(jsonPath("$.data.events[0].status").value("ACCEPTED"));
        verify(service).receive(eq("MOCK"), eq("1786406400"), eq("signature"), any(), eq("request-1"));
    }

    @Test
    void invalidSignatureUsesUnifiedUnauthorizedResponse() throws Exception {
        when(service.receive(any(), any(), any(), any(), any()))
                .thenThrow(new TrackingCallbackException("TRACK-1004", 401));

        mvc.perform(post("/api/v1/integrations/logistics/MOCK/tracking-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TRACK-1004"));
    }

    @Test
    void missingPermissionUsesUnifiedForbiddenResponse() throws Exception {
        when(service.receive(any(), any(), any(), any(), any()))
                .thenThrow(new TrackingCallbackException("COMMON-1004", 403));

        mvc.perform(post("/api/v1/integrations/logistics/MOCK/tracking-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("COMMON-1004"));
    }

    @Test
    void illegalMappingUsesUnifiedUnprocessableResponse() throws Exception {
        when(service.receive(any(), any(), any(), any(), any()))
                .thenThrow(new TrackingCallbackException("TRACK-1002", 422));

        mvc.perform(post("/api/v1/integrations/logistics/MOCK/tracking-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("TRACK-1002"));
    }
}
