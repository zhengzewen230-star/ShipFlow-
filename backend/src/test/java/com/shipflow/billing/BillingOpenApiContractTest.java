package com.shipflow.billing;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BillingOpenApiContractTest {
    @SuppressWarnings("unchecked")
    @Test
    void billingLifecycleContractMatchesControllerAndDto() throws Exception {
        Map<String, Object> document = new Yaml().load(Files.readString(Path.of("../openapi/shipflow-api.yaml")));
        Map<String, Object> paths = (Map<String, Object>) document.get("paths");
        Map<String, Object> components = (Map<String, Object>) document.get("components");
        Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");

        assertProperties(schemas, "BillBatch", "providerId", "fileName", "fileSize", "totalCount",
                "successCount", "failureCount", "duplicateCount", "importedAt", "status", "version");
        assertProperties(schemas, "BillDetail", "lineNo", "rawLineMasked", "errorMessage",
                "errorHandlingStatus", "createdAt", "updatedAt");
        assertProperties(schemas, "Reconciliation", "systemAmount", "billedAmount", "differenceAmount",
                "differenceReason", "reconciliationStatus", "responsibleUserId", "actionHistory", "version");
        assertProperties(schemas, "ReconciliationActionRequest", "remark", "version");

        for (String path : List.of("/billing/import-batches", "/reconciliations/{reconciliationId}/confirm",
                "/reconciliations/{reconciliationId}/reject", "/reconciliations/{reconciliationId}/comments")) {
            List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation(paths, path, "post").get("parameters");
            assertThat(parameters).anySatisfy(parameter -> assertThat(parameter.get("$ref"))
                    .isEqualTo("#/components/parameters/IdempotencyKey"));
        }

        assertThat(paths).containsKeys("/billing/import-batches/{batchId}",
                "/billing/import-batches/{batchId}/errors", "/billing/details", "/reconciliations",
                "/reconciliations/{reconciliationId}");
        assertThat(String.valueOf(operation(paths, "/reconciliations/{reconciliationId}/reject", "post").get("responses")))
                .contains("409", "422", "X-Trace-Id");
    }

    @SuppressWarnings("unchecked")
    private void assertProperties(Map<String, Object> schemas, String schemaName, String... names) {
        Map<String, Object> schema = (Map<String, Object>) schemas.get(schemaName);
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        assertThat(properties).containsKeys(names);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> operation(Map<String, Object> paths, String path, String method) {
        return (Map<String, Object>) ((Map<String, Object>) paths.get(path)).get(method);
    }
}
