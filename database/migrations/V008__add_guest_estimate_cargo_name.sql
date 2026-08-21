-- UTF-8, UTC. Extends the already-deployed V007 public estimate lead without changing formal quote data.
ALTER TABLE guest_estimate_lead
    ADD COLUMN cargo_name VARCHAR(80) NOT NULL DEFAULT '' AFTER cargo_type;
