# Fifth Phase Task List

| Task | Status | Evidence or next gate |
|---|---|---|
| Inspect real logistics schema and migration ceiling | PASS | Existing tables inspected; highest migration is V020 |
| Tenant public channel API | PASS | List, detail, and service-country GET contracts |
| Server-side filters, pagination, and sort whitelist | PASS | Controller, service, mapper XML, and frontend query tests |
| Public-field redaction | PASS | Price tiers, internal cost, supplier configuration, and secrets are excluded |
| Disabled channel quote/order guard | PASS | Quote and order service tests; no frontend-only enforcement |
| Snapshot preservation | PASS | Existing immutable quote/order snapshot paths unchanged |
| Platform admin UI | DEFERRED | Approved maintenance contract is incomplete |
| V021/V022 and store resource data | DEFERRED | No migration or business data write allowed in this phase |
| Detail mapper runtime failure | PASS | Fixed list-only MyBatis parameter expansion; JDK 21 regression tests pass |
| Backend restart after constructor injection fix | PASS | PID 30220, port 8080, health/OpenAPI available, Flyway disabled |
| Authenticated browser list/filter/detail acceptance | PASS | Existing merchant-admin session: US filter reduced 4 channels to 2, URL query survived reload, and public detail rendered redacted fields |
| Browser failure-path and disabled-channel write checks | BLOCKED | No safe business write, login-state mutation, or credential inspection was performed |
| OpenAPI count reconciliation | PASS | 109 static and 109 runtime operations; removed tenant price-rule detail was intentionally non-public |

## Exit conditions

- [x] No migration, DDL, DML, Flyway, commit, or push was run.
- [x] No historical business record was modified.
- [x] Tenant and platform scope boundaries remain explicit.
- [ ] Restore controlled-browser connectivity and repeat authenticated checks.
- [ ] Approve platform maintenance UI contract and P4-05/P4-06 resource migrations in a separate phase.
