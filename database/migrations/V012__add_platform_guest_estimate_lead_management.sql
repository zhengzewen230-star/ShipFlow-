-- UTF-8, UTC. Adds platform-only lead follow-up metadata; guest estimates remain outside tenant quotes and orders.
ALTER TABLE guest_estimate_lead
    ADD COLUMN handling_remark VARCHAR(500) NULL AFTER status,
    ADD COLUMN handled_by_user_id BIGINT NULL AFTER handling_remark,
    ADD COLUMN handled_at DATETIME(3) NULL AFTER handled_by_user_id,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER handled_at,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at,
    ADD COLUMN deleted TINYINT(1) NOT NULL DEFAULT 0 AFTER updated_at,
    ADD COLUMN deleted_at DATETIME(3) NULL AFTER deleted,
    ADD KEY idx_guest_estimate_status_created (status, created_at),
    ADD KEY idx_guest_estimate_created (created_at),
    ADD CONSTRAINT fk_guest_estimate_handler FOREIGN KEY (handled_by_user_id) REFERENCES sys_user(id);

ALTER TABLE guest_estimate_lead
    DROP CHECK chk_guest_estimate_status,
    ADD CONSTRAINT chk_guest_estimate_status CHECK (status IN ('RECEIVED', 'CONTACTING', 'QUALIFIED', 'CLOSED'));
