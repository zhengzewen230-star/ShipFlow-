# Warehouse and SF UAT Fulfillment Learning Notes

## Business flow

The merchant creates and submits an order. The warehouse operator confirms inbound, records DWS measurements, and moves the order to `READY_FOR_OUTBOUND`. The SF adapter then runs create, print, query, and certify upload. A valid outbound handover writes the tracking number, UTC batch number, and manifest reference in one transaction before moving the order to `OUTBOUND`.

Cancellation is a separate legal branch: an SF order can be cancelled before warehouse outbound. Cancellation after outbound is rejected with `SF-1009` and does not mutate provider state.

## Rules reinforced

- Sandbox provider records use `test_flag=1` and a UTC retention deadline 30 days after the call. No transaction performs physical cleanup.
- Strict mode treats HTTP 200 responses missing required references as `SfBusinessException(SF-1008)` and records provider state as `FAILED`.
- Sandbox mode fills UAT references for missing waybills, labels, and certify documents.
- Warehouse detail queries use a parameter-safe tenant-scoped SQL fragment; list-only filters are not evaluated for detail calls.
- Outbound handover identifiers are generated from UTC time and order id, and are written through the idempotent outbound transaction.

## Evidence

- Real MySQL database: `shipflow`; Flyway current version `016`.
- Orders 32 and 33 reached `OUTBOUND`; order 33 has non-null `batch_no` and `manifest_reference`.
- Order 34 reached provider `CANCELLED` while remaining `READY_FOR_OUTBOUND`; cancelling outbound order 33 returned HTTP 409 `SF-1009`.
- Backend targeted warehouse tests and XML tests passed; backend package and frontend build passed.
- Backend health, warehouse overview/detail, tracking events, and Vite entry returned HTTP 200.

## Open decisions

- Confirm the official SF sandbox field mapping for waybill, label, invoice, and certify references.
- Confirm the owner and policy for retention cleanup after the 30-day UAT window.
- Confirm whether UAT placeholder URLs are acceptable in the production-like frontend workflow.
