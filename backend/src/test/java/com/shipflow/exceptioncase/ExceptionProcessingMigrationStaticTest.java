package com.shipflow.exceptioncase;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class ExceptionProcessingMigrationStaticTest {
    @Test void schemaAndMigrationDeclareTheSameMinimumExceptionStructure() throws Exception {
        String schema = Files.readString(Path.of("../database/schema.sql"), StandardCharsets.UTF_8);
        String migration = Files.readString(Path.of("../database/migrations/V019__add_exception_processing_evidence.sql"), StandardCharsets.UTF_8);
        for (String token : new String[]{"responsible_party", "exception_handling_record", "exception_evidence_attachment", "content_sha256", "WAITING_PROVIDER_FEEDBACK", "PENDING_FINANCE_CONFIRMATION"}) {
            assertThat(schema).contains(token);
            assertThat(migration).contains(token);
        }
        assertThat(migration.toUpperCase()).doesNotContain("DROP TABLE", "TRUNCATE", "DELETE FROM");
    }
}
