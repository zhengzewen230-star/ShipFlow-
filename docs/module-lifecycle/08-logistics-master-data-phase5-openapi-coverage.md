# Fifth Phase OpenAPI Coverage

## Tenant operations

| Method | Path | Implementation | Visibility |
|---|---|---|---|
| GET | `/api/v1/logistics/channels` | `TenantLogisticsController.channels` | Tenant scope plus `logistics:read` |
| GET | `/api/v1/logistics/channels/{channelId}` | `TenantLogisticsController.channel` | Tenant scope plus `logistics:read`; missing/unavailable resource is `COMMON-1006` |
| GET | `/api/v1/logistics/channels/{channelId}/service-countries` | `TenantLogisticsController.serviceCountries` | Tenant scope plus `logistics:read` |

The static OpenAPI document describes these three GET operations and their 400/401/403/404 responses. The runtime `/v3/api-docs` endpoint was checked for HTTP 200 after the current backend restart.

## 110-to-109 contract reconciliation

The former expected count of 110 was `106 + 4` store-resource operations, but that inventory also retained one removed tenant operation: `getEffectivePublishedPriceRule`, `GET /api/v1/logistics/channels/{channelId}/price-rule`. It returned a complete `PublishedPriceRule`, including price-rule tiers, and would violate the Phase 5 public-field boundary. The endpoint has no controller mapping, no frontend client call, and is intentionally not restored. The unused application-service and mapper query entry were removed so this tenant exposure cannot be reintroduced accidentally.

The four store-resource operation IDs remain in the static contract. Static OpenAPI and runtime `/v3/api-docs` each contain 109 unique operations. This difference is PASS: it is a documented public-contract removal, not a count-only adjustment.

## Platform operations

Provider, channel, service-country, and published-price-rule operations remain under `/api/v1/platform/*`. Their existing OpenAPI operations are retained and secured by `scope:PLATFORM`; merchant users do not receive a platform price-rule write operation.

## Contract boundary

No frontend operation is added for platform maintenance. No undocumented GET mapping is added for a POST-only business operation. The public DTO intentionally does not model price tiers, internal cost, supplier configuration, or secrets.
