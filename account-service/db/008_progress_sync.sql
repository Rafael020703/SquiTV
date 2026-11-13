SET LOCAL ROLE squitv_account_owner;

ALTER TABLE squitv.watch_progress
    ADD COLUMN observed_ms bigint NOT NULL DEFAULT 0 CHECK (observed_ms >= 0),
    ADD COLUMN catalog_id bigint CHECK (catalog_id > 0),
    ADD COLUMN title text CHECK (length(title) <= 240);

GRANT SELECT, INSERT, UPDATE ON squitv.watch_progress TO squitv_iptv_portal;
CREATE INDEX watch_progress_revision_feed ON squitv.watch_progress (account_id, profile_id, link_id, revision);
INSERT INTO squitv.schema_migrations (version) VALUES (8);

RESET ROLE;