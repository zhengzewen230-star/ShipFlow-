# Merchant Store Management Phase 4 Runtime Verification

Verification time: 2026-08-19 Asia/Shanghai

## Scope and safety boundary

This verification covers the current backend runtime, authentication refresh contract, price-confirmation request contract, and the real store-detail empty-resource presentation. It does not execute V021/V022, DDL, DML, or Flyway, and it does not modify `merchant_store`, orders, quotes, tracking, billing, or historical snapshots.

## Runtime result

The previous backend instance PID 20792 was stopped after confirming its process identity. The project Maven Spring Boot run command was restarted with JDK 21.0.11, profile `local`, Flyway disabled, and the repository-local JWT key files. The active Java process is PID 21168. Startup logs report Spring Boot 3.5.16 and Tomcat on port 8080.

The original refresh 405/401 runtime symptom was caused by an old backend instance that had not loaded the current authentication implementation. The current runtime was started from the current compiled classes. `GET /api/v1/auth/refresh` returns HTTP 405 with `Allow: POST`; `/actuator/health` and `/v3/api-docs` return HTTP 200.

The frontend contract remains `POST /api/v1/auth/refresh` with Axios `withCredentials: true`. The interceptor shares one refresh Promise for concurrent 401 responses, retries an original request at most once after a successful refresh, and performs one session-expiry cleanup/redirect when refresh fails. Price confirmation remains `POST /api/v1/orders/{orderId}/price-confirmation` and keeps its idempotency key and version checks.

## Store detail result

The current store data has no configured address, channel, or country/region resource. The detail page therefore shows:

- Country/region: `尚未配置`
- Default shipping address: `尚未配置`
- Default logistics channel: `尚未配置`
- Audit summary with no records: `暂无审计记录`

These values are real empty-resource states, not fabricated defaults. The API continues to enforce tenant, user, role, and active store-scope visibility; unauthorized resources remain 404.

## Phase classification

| Area | Status | Evidence and boundary |
|---|---|---|
| Store-management code and API contracts | PASS | Current backend/frontend implementation and focused tests pass. |
| Refresh POST-only runtime contract | PASS | Current PID 21168 returns 405 for GET, with `Allow: POST`. |
| Refresh success/failure browser flow | BLOCKED | A fresh authenticated browser refresh and the refresh-failure cleanup path require controlled-session evidence; no credentials or tokens are recorded here. |
| Real price-confirmation submission | BLOCKED | No business-eligible confirmation was submitted in this verification, so success is not claimed. |
| Store detail empty-resource presentation | PASS | Controlled browser showed the four real empty-state labels above. |
| Address/channel/country business data | DEFERRED | Resource rows remain unconfigured because the approved migration/backfill work is deferred. |
| V021/V022, database migration, and database writes | DEFERRED | No migration, DDL, DML, or Flyway execution was performed; execution-side business write count is 0. |

Phase 4 is therefore not a claim that all business data is populated. The implementation is complete within the current schema and authorization boundary, while resource-data migration/backfill and the remaining credential-gated browser paths remain DEFERRED or BLOCKED as classified above.

