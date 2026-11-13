SET LOCAL ROLE squitv_account_owner;

ALTER TABLE squitv.provider_links
    ADD COLUMN slot smallint NOT NULL DEFAULT 1 CHECK (slot IN (1, 2)),
    DROP CONSTRAINT provider_links_pkey,
    ADD PRIMARY KEY (account_id, slot);

GRANT SELECT (slot), INSERT (slot) ON squitv.provider_links TO squitv_iptv_portal;

INSERT INTO squitv.schema_migrations (version) VALUES (4);

RESET ROLE;