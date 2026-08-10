# shipflow_qa Database Cleanup Safety Plan

- Status: read-only script comparison and metadata inventory completed.
- Inventory time: 2026-08-09, Asia/Shanghai.
- Target database: `shipflow_qa`.
- Database guard: a fresh `SELECT DATABASE()` returned exactly `shipflow_qa`.
- Connection source: only `SHIPFLOW_QA_DB_*` environment variables were read.
- Queries executed: `SELECT DATABASE()`, `SHOW TABLES`, and read-only `information_schema` table and foreign-key dependency queries.
- Not executed: `DROP`, `DELETE`, `TRUNCATE`, `ALTER`, `UPDATE`, or `INSERT`.
- Not operated: `shipflow` or `shipflow_test`.
- Sensitive data: no password, token, cookie, or complete connection string is recorded here.

## 1. Script comparison

| Script | Database/object scope established by the file |
|---|---|
| `database/schema.sql` | `shipflow`; defines the 31 business tables listed below. The file itself contains destructive setup statements, but none were executed in this review. |
| `database/init_data.sql` | Business seed data for `shipflow`: `tenant`, `sys_permission`, `sys_user`, `sys_role`, `sys_user_role`, `sys_role_permission`, `merchant_store`, `logistics_provider`, `logistics_channel`, `logistics_channel_service_country`, `price_rule`, and `price_rule_tier`. |
| `database/qa/001_create_api_test_case.sql` | `shipflow_qa.api_test_case` creation. |
| `database/qa/002_seed_auth_api_test_cases.sql` | Inserts QA cases into `shipflow_qa.api_test_case`. |
| `database/qa/003_upgrade_api_test_case_execution_contract.sql` | Changes and validates only `shipflow_qa.api_test_case`; its procedure cleanup is also scoped to that object. |
| `database/qa/004_fix_login_case_teardown.sql` | Updates only `shipflow_qa.api_test_case`. |
| `database/qa/005_fix_auth_login_004_assertion.sql` | Updates only `shipflow_qa.api_test_case`. |

The script comparison proves that the QA scripts require `api_test_case` only. It does not, by itself, prove that business tables in the QA database are unused or safe to delete.

## 2. List 1: explicitly retain

| Table | Source and evidence | Current metadata |
|---|---|---:|
| `api_test_case` | Created and seeded by QA scripts 001-005; required by the QA test executor. | `TABLE_ROWS` estimate: 52; no foreign keys. |

## 3. List 2: explicitly safe to delete

**None.**

No table has sufficient evidence for an explicit deletion decision. The current metadata shows non-zero estimates in several business tables, and the 31 business tables participate in an internal foreign-key dependency graph. Script scope alone cannot establish data ownership, active consumers, backup availability, or approval to remove them.

Because this list is empty, there are no tables for which deletion order, backup, or rollback can be approved in this review. No deletion SQL is generated or executed.

## 4. List 3: unresolved and requiring manual confirmation

All 31 business tables remain in this list:

`api_idempotency_record`, `audit_log`, `auth_refresh_session`, `bill_detail`, `bill_import_batch`, `claim_record`, `exception_case`, `fee_adjustment`, `logistics_channel`, `logistics_channel_service_country`, `logistics_provider`, `merchant_store`, `price_rule`, `price_rule_tier`, `quote`, `reconciliation_record`, `shipment_address`, `shipment_item`, `shipment_order`, `shipment_package`, `shipment_quote_snapshot`, `sys_permission`, `sys_role`, `sys_role_permission`, `sys_user`, `sys_user_role`, `tenant`, `tracking_event`, `warehouse_measurement`, `warehouse_outbound_record`.

### Per-table evidence

| Table | Source script | Current `TABLE_ROWS` estimate | Foreign-key dependencies | Decision |
|---|---|---:|---|---|
| `api_idempotency_record` | `schema.sql` | 0 | none | Manual confirmation |
| `audit_log` | `schema.sql` | 0 | references `sys_user`, `tenant` | Manual confirmation |
| `auth_refresh_session` | `schema.sql` | 0 | references `auth_refresh_session`, `sys_user`, `tenant` | Manual confirmation |
| `bill_detail` | `schema.sql` | 0 | references `bill_import_batch`, `logistics_provider`, `shipment_order`, `tenant` | Manual confirmation |
| `bill_import_batch` | `schema.sql` | 0 | references `logistics_provider`, `tenant` | Manual confirmation |
| `claim_record` | `schema.sql` | 0 | references `exception_case`, `tenant` | Manual confirmation |
| `exception_case` | `schema.sql` | 0 | references `shipment_order`, `tenant` | Manual confirmation |
| `fee_adjustment` | `schema.sql` | 0 | references `shipment_order`, `sys_user`, `tenant`, `warehouse_measurement` | Manual confirmation |
| `logistics_channel` | `schema.sql`, `init_data.sql` | 4 | references `logistics_provider` | Manual confirmation |
| `logistics_channel_service_country` | `schema.sql`, `init_data.sql` | 7 | references `logistics_channel` | Manual confirmation |
| `logistics_provider` | `schema.sql`, `init_data.sql` | 2 | none | Manual confirmation |
| `merchant_store` | `schema.sql`, `init_data.sql` | 4 | references `tenant` | Manual confirmation |
| `price_rule` | `schema.sql`, `init_data.sql` | 5 | references `logistics_channel` | Manual confirmation |
| `price_rule_tier` | `schema.sql`, `init_data.sql` | 10 | references `price_rule` | Manual confirmation |
| `quote` | `schema.sql` | 0 | references `logistics_channel`, `merchant_store`, `price_rule`, `tenant` | Manual confirmation |
| `reconciliation_record` | `schema.sql` | 0 | references `bill_detail`, `shipment_order`, `sys_user`, `tenant` | Manual confirmation |
| `shipment_address` | `schema.sql` | 0 | references `shipment_order`, `tenant` | Manual confirmation |
| `shipment_item` | `schema.sql` | 0 | references `shipment_package`, `tenant` | Manual confirmation |
| `shipment_order` | `schema.sql` | 0 | references `logistics_channel`, `merchant_store`, `quote`, `tenant` | Manual confirmation |
| `shipment_package` | `schema.sql` | 0 | references `shipment_order`, `tenant` | Manual confirmation |
| `shipment_quote_snapshot` | `schema.sql` | 0 | references `price_rule`, `quote`, `shipment_order`, `tenant` | Manual confirmation |
| `sys_permission` | `schema.sql`, `init_data.sql` | 19 | none | Manual confirmation |
| `sys_role` | `schema.sql`, `init_data.sql` | 10 | references `tenant` | Manual confirmation |
| `sys_role_permission` | `schema.sql`, `init_data.sql` | 46 | references `sys_permission`, `sys_role` | Manual confirmation |
| `sys_user` | `schema.sql`, `init_data.sql` | 10 | references `tenant` | Manual confirmation |
| `sys_user_role` | `schema.sql`, `init_data.sql` | 10 | references `sys_role`, `sys_user`, `tenant` | Manual confirmation |
| `tenant` | `schema.sql`, `init_data.sql` | 2 | none | Manual confirmation |
| `tracking_event` | `schema.sql` | 0 | references `logistics_provider`, `shipment_order`, `tenant` | Manual confirmation |
| `warehouse_measurement` | `schema.sql` | 0 | references `shipment_package`, `sys_user`, `tenant` | Manual confirmation |
| `warehouse_outbound_record` | `schema.sql` | 0 | references `logistics_provider`, `shipment_order`, `shipment_package`, `sys_user`, `tenant` | Manual confirmation |

The `TABLE_ROWS` values above are InnoDB estimates, not exact counts. No business-table row content was read.

## 5. Conditional deletion order if later approved

This is planning information only, not an executable command list. Because there are currently no approved deletion candidates, no order is authorized. If the owner later approves the complete business schema for removal, the foreign-key-safe direction is child tables before parent tables, for example:

1. `audit_log`, `auth_refresh_session`, `reconciliation_record`, `claim_record`, `fee_adjustment`, `warehouse_outbound_record`, `tracking_event`, `shipment_address`, `shipment_item`, `shipment_quote_snapshot`, `warehouse_measurement`, `bill_detail`, `exception_case`, `shipment_package`, `shipment_order`, `quote`, `price_rule_tier`, `sys_user_role`, `sys_role_permission`, `logistics_channel_service_country`.
2. `bill_import_batch`, `warehouse_measurement` if not already removed by its dependents, `price_rule`, `merchant_store`, `logistics_channel`.
3. `sys_user`, `sys_role`, `logistics_provider`.
4. `sys_permission`, `tenant`, `api_idempotency_record`.

The exact order must be regenerated after owner approval because shared parents and self-references require a final dependency check. `api_test_case` is excluded from every deletion plan.

## 6. Required backup and rollback design for any future approved table

No table is currently approved, so no table-specific backup or rollback has been performed. For any later approved table, the minimum process is:

- Backup: export the approved table structure and rows to a controlled directory outside the repository; record a checksum and the `shipflow_qa` database guard result. Do not store the backup in Git, reports, or logs.
- Dependency capture: save the approved table's inbound and outbound foreign-key metadata before any change.
- Rollback: restore structure and rows from the verified external backup in an isolated environment first, then obtain explicit owner approval before any production-like restoration. Recheck `SELECT DATABASE()` before restoration.
- Safety boundary: do not use `shipflow` or `shipflow_test`; do not run any destructive statement in this phase.

## 7. Stop point and owner questions

The final deletion list is explicitly empty. Work stops here pending confirmation of the unresolved list.

1. Are these 31 business tables accidental initialization in `shipflow_qa`, or approved QA baseline objects?
2. Who owns the non-zero baseline data and approves its removal?
3. Is an external backup and restore test approved, and where may it be stored?
4. Is Flyway or another deployment history available to verify how these tables entered `shipflow_qa`?
