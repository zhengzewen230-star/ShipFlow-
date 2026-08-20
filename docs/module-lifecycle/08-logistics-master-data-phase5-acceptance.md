# Fifth Phase Acceptance

## Result matrix

| Capability | Result | Evidence boundary |
|---|---|---|
| Tenant channel filters, paging, sorting, URL restore | PASS in code and frontend tests | Real API contract and unit tests |
| Tenant channel detail and service countries | PASS in code and controller tests | The detail mapper parameter-binding defect was fixed; public DTO omits non-public fields |
| Merchant cannot manage platform price rules | PASS by route security and UI scope | `scope:PLATFORM` plus `price-rule:manage` is required |
| Disabled channel blocked for new quotes/orders | PASS in service tests and backend guards | No business write was manufactured in the browser |
| Historical order/quote snapshots preserved | PASS by unchanged snapshot paths and no migration/write | Existing historical data was not modified |
| Platform maintenance UI | DEFERRED | No approved complete UI contract |
| Browser basic authenticated read-only acceptance | PASS | Existing merchant-admin session verified list, US filter, URL restore, and public channel detail without a business write |
| Browser 401/403/network/disabled-channel failure-path acceptance | BLOCKED | No safe trigger was executed; no login state, credentials, or business data was altered |
| OpenAPI 110-to-109 reconciliation | PASS | Intentional removal of tenant `getEffectivePublishedPriceRule`; static and runtime contracts each have 109 unique operations |
| P4-05/P4-06 resources and V021/V022 | DEFERRED | Migration directory ends at V020; no migration executed |

## Non-fabrication statement

No channel, service country, price rule, status, order, quote, audit record, or store resource was fabricated for acceptance. No password, token, cookie, authorization header, private key, or supplier secret was output.

## Runtime incident closure

The real `/api/v1/logistics/channels/4` 500 was caused by `findPublicChannel` reusing the list filter SQL despite accepting only `channelId`; MyBatis reported a missing `channelCode` parameter. The query now contains only the channel ID and base provider/channel visibility predicates, and the regression test passes. A second startup issue caused by the order service's unannotated overloaded constructors was also fixed with explicit constructor injection. The restarted backend is PID 30220, JDK 21.0.11, port 8080.

## Database gate

Database writes during this phase: `0`. V021/V022, DDL, DML, and Flyway execution: `0`. Historical orders, quotes, tracking, bills, and snapshots: unchanged.

## Closure decision

The code scope is PASS and the OpenAPI difference is PASS. Basic authenticated browser read-only acceptance is PASS; failure-path and business-write acceptance remains BLOCKED. Database/resource migrations and platform maintenance UI remain DEFERRED. The Phase 5 code review can close, but it is not a full end-to-end business acceptance until those separately gated items are completed.
