SET LOCAL ROLE squitv_account_owner;

ALTER TABLE squitv.provider_links
    DROP CONSTRAINT provider_links_slot_check,
    ADD CONSTRAINT provider_links_slot_check CHECK (slot BETWEEN 1 AND 20);

INSERT INTO squitv.schema_migrations (version) VALUES (5);

RESET ROLE;