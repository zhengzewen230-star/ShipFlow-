-- Phase 7 order-management read indexes. UTC timestamps; no business rows are inserted, updated or deleted.
-- Rollback (after confirming no dependent query plan):
--   ALTER TABLE shipment_order DROP INDEX idx_order_tenant_no_created, DROP INDEX idx_order_tenant_destination_created;
--   ALTER TABLE warehouse_outbound_record DROP INDEX idx_outbound_tenant_tracking_order;

ALTER TABLE shipment_order
    ADD KEY idx_order_tenant_no_created (tenant_id, order_no, created_at),
    ADD KEY idx_order_tenant_destination_created (tenant_id, destination_country, created_at);

ALTER TABLE warehouse_outbound_record
    ADD KEY idx_outbound_tenant_tracking_order (tenant_id, tracking_no, shipment_order_id);
