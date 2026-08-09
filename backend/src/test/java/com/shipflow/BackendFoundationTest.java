package com.shipflow;

import com.shipflow.common.api.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.shipflow.store.mapper.StoreMapper;
import com.shipflow.store.mapper.StoreIdempotencyMapper;
import com.shipflow.store.mapper.StoreAuditMapper;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BackendFoundationTest {

    @MockBean StoreMapper storeMapper;
    @MockBean StoreIdempotencyMapper storeIdempotencyMapper;
    @MockBean StoreAuditMapper storeAuditMapper;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unifiedSuccessResponseContainsTraceId() throws Exception {
        mockMvc.perform(get("/_test/ok"))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("ok"));
    }

    @Test
    void traceIdIsPropagated() throws Exception {
        mockMvc.perform(get("/_test/ok").header("X-Trace-Id", "test-trace-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Trace-Id", "test-trace-id"))
                .andExpect(jsonPath("$.traceId").value("test-trace-id"));
    }

    @Test
    void globalExceptionIsMappedToCommonError() throws Exception {
        mockMvc.perform(get("/_test/error"))
                .andExpect(status().isInternalServerError())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-1007"));
    }

    @Test
    void validationExceptionIsMappedToBadRequest() throws Exception {
        mockMvc.perform(post("/_test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.error.code").value("COMMON-1001"))
                .andExpect(jsonPath("$.error.details.name").exists());
    }

    @Test
    void healthEndpointIsAvailable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void responseRecordHasExpectedSuccessShape() {
        ApiResponse<String> response = ApiResponse.success("value");
        assertThat(response.success()).isTrue();
        assertThat(response.data()).isEqualTo("value");
    }
}
