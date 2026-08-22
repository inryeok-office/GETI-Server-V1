ALTER TABLE audit_logs
    ADD COLUMN result VARCHAR(20),
    ADD COLUMN result_message TEXT,
    ADD COLUMN request_path VARCHAR(1000);

CREATE INDEX idx_audit_logs_admin_order
    ON audit_logs (created_at DESC, id DESC);
