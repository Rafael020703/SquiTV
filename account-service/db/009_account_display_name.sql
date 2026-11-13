SET LOCAL ROLE squitv_account_owner;

ALTER TABLE squitv.accounts
    ADD COLUMN display_name text CHECK (length(btrim(display_name)) BETWEEN 1 AND 80);
GRANT SELECT (display_name), INSERT (display_name), UPDATE (display_name)
    ON squitv.accounts TO squitv_iptv_portal;

INSERT INTO squitv.schema_migrations (version) VALUES (9);

RESET ROLE;