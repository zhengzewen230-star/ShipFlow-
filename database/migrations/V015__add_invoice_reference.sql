-- Requires controlled Flyway execution after V013 and V014.
-- Stores the non-sensitive invoice reference separately from the label reference.
ALTER TABLE shipment_label
    ADD COLUMN invoice_reference VARCHAR(255) NULL AFTER label_reference;
