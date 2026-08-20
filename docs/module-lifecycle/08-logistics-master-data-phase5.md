# Fifth Phase: Logistics Master Data

## Baseline comparison

| Fifth-phase requirement | Existing implementation | Gap or error | Minimal resolution |
|---|---|---|---|
| Merchant channel list with filters, paging, and sorting | `GET /api/v1/logistics/channels` uses the real `logistics_channel`, `logistics_provider`, `logistics_channel_service_country`, and effective published `price_rule` rows | The old tenant response reused a platform model and did not expose the complete query contract | Use the public projection DTO, server-side filter validation, and a sort whitelist |
| Public channel details and service countries | Detail and service-country GET endpoints return provider, channel, status, service countries, effective rule summary, and `unavailableFields` | Cargo attributes and public price explanation have no approved source fields | Return them as unavailable; do not infer or expose price tiers or internal configuration |
| Tenant security boundary | Security requires `scope:TENANT` and `logistics:read`; platform routes require `scope:PLATFORM` | Public master data is platform-owned and has no tenant column | Keep platform ownership explicit; do not add tenant-only SQL or broaden roles |
| Disabled channel rule | Quote creation validates channel status and service country; order creation revalidates channel status | A real disabled-channel write was not safe to manufacture in the browser | Keep backend rejection and service tests; browser write verification remains blocked |
| Historical snapshots | Quote and order creation writes immutable snapshots; no history update is part of this phase | No approved migration or data fixture is available for new store bindings | Preserve existing snapshots and defer P4-05/P4-06 resource data |
| Platform maintenance | Existing platform Controller/Service/Mapper contracts retain role, scope, version, idempotency, and success audit checks | No approved feature-complete platform UI contract exists | Keep platform UI deferred and add no pseudo-actions |

## Scope and workflow

Platform administrators own providers, channels, service countries, and published price rules. Tenant users can read only the public channel catalogue. Quote creation validates an ACTIVE store, ACTIVE channel, destination coverage, and an effective published rule. Order creation revalidates the store and channel inside its idempotent transaction before writing the immutable quote snapshot. Channel deactivation therefore prevents new quotes and orders, while existing quotes, orders, and snapshots remain unchanged and queryable.

## Delivered capability

- `GET /api/v1/logistics/channels` supports channel code, channel name, service country, status, paging, and a server-side whitelist of sort fields.
- Tenant responses contain provider, channel, transport, service countries, active rule version/time, volume divisor, and status. They explicitly mark unavailable or non-public fields and do not return price tiers, internal cost, or supplier configuration.
- The tenant UI restores filters from URL query parameters, distinguishes loading/empty/error states through `DataState`, carries trace IDs on API failures, and provides a read-only details route.
- Existing platform maintenance endpoints remain platform-only. No platform management UI was added because the repository has no complete approved maintenance workflow for it.
- Order creation now checks the quoted channel is still ACTIVE before inserting an order or snapshot.

## Security and data boundary

All tenant catalogue routes retain `scope:TENANT` plus `logistics:read`; platform writes retain `scope:PLATFORM`, `logistics:manage` or `price-rule:manage`, optimistic version checks, idempotency, transactions, and audit insertion. The catalogue is platform-owned public master data and has no tenant_id by design; tenant-specific store bindings remain deferred and are not inferred.

No V021/V022, DDL, DML, Flyway execution, warehouse master-data creation, or historical order/quote/tracking/billing/snapshot mutation is part of this phase. P4-05/P4-06 resource data remains DEFERRED because `merchant_store_address` and `merchant_store_channel` have no approved source rows.

## Deferred and blocked acceptance

- Platform maintenance UI: DEFERRED; backend contract exists but no approved feature-complete UI contract is present.
- Live browser failure-path checks (401, network failure, cookie panel): BLOCKED unless a real authenticated browser session and a safe trigger are supplied.
- A real disabled-channel order rejection cannot be manufactured without an authorized business write and is verified by service tests only.

## Closure review: OpenAPI and browser

The baseline arithmetic of 110 was corrected after source comparison. The missing operation is `getEffectivePublishedPriceRule` (`GET /api/v1/logistics/channels/{channelId}/price-rule`), not a store-resource API. It intentionally returned full price-rule tiers to tenants, contrary to the Phase 5 public-data boundary. The current public detail projection provides only rule version, effective time, and volume divisor; it does not expose tier fees, internal cost, or supplier configuration. Static OpenAPI and runtime OpenAPI both expose 109 unique operations, so the contract difference is PASS.

Basic browser acceptance is PASS. The existing merchant-admin session rendered the public catalogue, restored the US filter after reload, and displayed a public channel detail without price tiers, internal cost, or supplier configuration. Browser failure paths and disabled-channel business writes remain BLOCKED because no login-state mutation or business write was performed. Database migrations/resources and platform maintenance UI remain DEFERRED.

## Database and runtime gate

The inspected project schema uses `logistics_channel`, `logistics_channel_service_country`, `price_rule`, `price_rule_tier`, `merchant_store_channel`, and `audit_log`. The migration directory ends at V020; V021 and V022 are absent and were not executed. This phase made zero database writes and did not run DDL, DML, Flyway, or any migration.

The current local backend is the JDK 21.0.11 process started from the project backend directory on port 8080. Runtime health and OpenAPI are available. Browser acceptance is BLOCKED because the controlled browser cannot connect to the local Vite listener; no authenticated session or business write was fabricated.
