package com.shipflow.exceptioncase;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import static org.assertj.core.api.Assertions.assertThat;
class ExceptionClaimMapperXmlTest {
 @Test void xmlEnforcesTenantStateIdempotencyAuditAndFinancialBoundaries()throws Exception{
  String x=Files.readString(Path.of("src/main/resources/mapper/exceptioncase/ExceptionClaimMapper.xml"));
  assertThat(x).contains("e.tenant_id=#{tenantId}","c.tenant_id=#{tenantId}","tracking_event WHERE tenant_id=#{tenantId}","status=#{fromStatus} AND version=#{version}","responsible_party","exception_handling_record","exception_evidence_attachment","content_sha256","FOR UPDATE","api_idempotency_record","INSERT INTO audit_log","EXCEPTION_ASSIGN","JSON_EXTRACT","accessibleStorePredicate","sys_user_store_scope","uss.status='ACTIVE'","accessibleWorkbenchFilter","PENDING_FOLLOW_UP","WAITING_PROVIDER_FEEDBACK")
   .doesNotContain("SELECT *","UPDATE shipment_order","UPDATE shipment_quote_snapshot","UPDATE fee_adjustment","UPDATE warehouse_measurement","UPDATE tracking_event","estimated_fee=","current_fee=");
 }
 @Test void xmlParsesNewHandlingAndEvidenceStatements() throws Exception {
  Configuration configuration = new Configuration();
  try (var input = Files.newInputStream(Path.of("src/main/resources/mapper/exceptioncase/ExceptionClaimMapper.xml"))) {
   new XMLMapperBuilder(input, configuration, "exceptioncase", configuration.getSqlFragments()).parse();
  }
  assertThat(configuration.hasStatement("com.shipflow.exceptioncase.mapper.ExceptionClaimMapper.findHandlingRecords")).isTrue();
  assertThat(configuration.hasStatement("com.shipflow.exceptioncase.mapper.ExceptionClaimMapper.insertEvidence")).isTrue();
 }
}
