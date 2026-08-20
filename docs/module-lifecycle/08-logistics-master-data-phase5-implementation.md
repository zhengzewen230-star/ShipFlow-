# Fifth Phase Implementation

## Scope

This implementation is limited to the tenant read-only logistics catalogue and the backend channel-state guard. It uses the existing platform master-data tables and does not depend on V021/V022, warehouse master data, or store resource rows.

## Changes

- Added a tenant-safe public projection and paged query for channel code, name, service country, status, page size, and server-side sort.
- Added read-only channel detail and service-country endpoints.
- Removed tenant exposure of price tiers, internal cost, supplier configuration, and secret-bearing provider configuration.
- Revalidated channel `ACTIVE` during order creation after loading the tenant quote. Quote creation already validates channel status, service-country coverage, and an effective published rule.
- Fixed the public channel detail mapper query so it no longer expands list-only filter parameters (`channelCode`, `channelName`, `serviceCountry`, `status`) for the single-parameter `channelId` lookup. Added a mapper XML regression test for this boundary.
- Added explicit constructor injection for the order service after the disabled-channel guard introduced a second constructor; the local backend now starts successfully with the full application context.
- Added frontend URL-query restoration, filter state, pagination, sorting, detail display, retry, and trace-id presentation.
- Kept platform maintenance controls out of the merchant UI because the repository does not contain an approved complete platform-maintenance workflow.

## Transaction and snapshot boundary

The existing quote and order write paths retain idempotency and transaction boundaries. The order path writes its existing immutable quote/address/package snapshots only after validation. This phase does not update or delete orders, quotes, tracking events, bills, or historical snapshots.

## Deferred items

P4-05/P4-06 store resource migration, V021/V022, warehouse master data, and store default address/channel bindings remain `DEFERRED`. Platform maintenance UI and a real disabled-channel browser write are also deferred or blocked as described by the acceptance document.

## Runtime verification

- The previous authenticated detail request failed with HTTP 500 because MyBatis evaluated list-only parameters in `findPublicChannel`; this was a code mapping defect, not a missing migration or missing resource row.
- The backend was restarted from the existing `start-backend.ps1` flow with JDK 21.0.11. Current PID is 30220 on port 8080; Flyway remains disabled.
- Anonymous runtime checks returned health 200, OpenAPI 200, `GET /api/v1/auth/refresh` 405, and anonymous channel detail 401. The controlled browser is currently on the login page, so authenticated detail rendering remains BLOCKED until a user-authenticated session is available.
