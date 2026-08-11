package com.shipflow.exceptioncase;
import org.junit.jupiter.api.Test;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;
class ExceptionClaimMapperXmlTest {
 @Test void xmlEnforcesTenantStateIdempotencyAuditAndFinancialBoundaries()throws Exception{
  String x=Files.readString(Path.of("src/main/resources/mapper/exceptioncase/ExceptionClaimMapper.xml"));
  assertThat(x).contains("e.tenant_id=#{tenantId}","c.tenant_id=#{tenantId}","tracking_event WHERE tenant_id=#{tenantId}","status=#{fromStatus} AND version=#{version}","FOR UPDATE","api_idempotency_record","INSERT INTO audit_log","EXCEPTION_ASSIGN","JSON_EXTRACT")
   .doesNotContain("SELECT *","UPDATE shipment_order","UPDATE shipment_quote_snapshot","UPDATE fee_adjustment","UPDATE warehouse_measurement","UPDATE tracking_event","estimated_fee=","current_fee=");
 }
}
