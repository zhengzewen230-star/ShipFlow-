# Phase 5 Learning Notes

- Public catalogue DTOs must be distinct from platform price-rule DTOs. Reusing a server-side model that contains `tiers`, currency, and fee values silently expands a tenant-facing contract.
- Channel status is a creation-time rule. Both quote and order creation validate it, while historical records retain their existing channel and immutable price snapshot.
- Sort field names are never interpolated from the request. The application normalizes a fixed whitelist before MyBatis chooses the matching SQL column.
- Public fields without a reliable source are returned as absent with an explicit unavailable-field marker; the UI does not invent cargo attributes or price explanations.
