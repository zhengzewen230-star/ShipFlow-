# Fifth Phase Permission Matrix

| Role or scope | Tenant public catalogue | Platform channel/rule maintenance | Quote/order effect |
|---|---|---|---|
| `MERCHANT_OPERATOR` (`scope:TENANT`) | Read permitted public fields only with `logistics:read` | No access | Existing quote/order permissions still apply; disabled channels are rejected server-side |
| `MERCHANT_ADMIN` (`scope:TENANT`) | Read permitted public fields only with `logistics:read` | No access to platform price rules | May use existing tenant permissions; this phase does not add store-resource writes |
| `PLATFORM_ADMIN` (`scope:PLATFORM`) | Does not use the tenant catalogue route | Existing platform routes require platform scope and the relevant `logistics:manage` or `price-rule:manage` authority | Platform maintenance remains backend-only/deferred in the UI |
| Finance, warehouse, and customer-service roles | No new broad logistics permission is added | No platform maintenance permission | Existing order, warehouse, billing, or tracking permissions remain unchanged |

All tenant business-resource queries continue to derive `tenant_id` from the authenticated identity and apply existing resource/scope checks. The public master catalogue is platform-owned; it is not converted into a tenant-only table or query.
