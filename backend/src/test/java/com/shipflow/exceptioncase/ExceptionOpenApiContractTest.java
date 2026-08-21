package com.shipflow.exceptioncase;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionOpenApiContractTest {
    @SuppressWarnings("unchecked")
    @Test void exceptionAndClaimContractMatchesControllerRequirements() throws Exception {
        Map<String, Object> document = new Yaml().load(Files.readString(Path.of("../openapi/shipflow-api.yaml")));
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        Map<String, Object> list = operation(paths, "/exceptions", "get");
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) list.get("parameters");
        assertThat(parameters.stream().map(p -> p.get("name")).filter(java.util.Objects::nonNull))
                .contains("exceptionType", "status", "orderNo", "storeId", "responsibleParty", "createdFrom", "createdTo", "sortBy", "sortDirection");

        for (String path : List.of("/orders/{orderId}/exceptions", "/exceptions/{exceptionId}/assign",
                "/exceptions/{exceptionId}/status", "/exceptions/{exceptionId}/handling-records",
                "/exceptions/{exceptionId}/evidence", "/exceptions/{exceptionId}/claim",
                "/claims/{claimId}/submit", "/claims/{claimId}/result",
                "/claims/{claimId}/finance-confirmation", "/claims/{claimId}/close")) {
            List<Map<String, Object>> writeParameters = (List<Map<String, Object>>) operation(paths, path, "post").get("parameters");
            assertThat(writeParameters).anySatisfy(parameter -> assertThat(parameter.get("$ref"))
                    .isEqualTo("#/components/parameters/IdempotencyKey"));
        }

        Map<String, Object> claimResult = (Map<String, Object>) schemas.get("ClaimResultRequest");
        Map<String, Object> properties = (Map<String, Object>) claimResult.get("properties");
        assertThat(properties).containsKeys("approvedAmount", "version");
        assertThat(String.valueOf(properties.get("status"))).contains("PARTIALLY_APPROVED");
        assertThat(paths).containsKey("/claims/{claimId}/finance-confirmation");
        assertThat(String.valueOf(schemas.get("ExceptionCaseView"))).contains("handlingRecords", "evidenceAttachments", "timeline", "assignedToUserName");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
    }
}
