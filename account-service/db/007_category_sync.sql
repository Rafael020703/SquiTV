SET LOCAL ROLE squitv_account_owner;

GRANT SELECT, INSERT, UPDATE ON squitv.category_preferences TO squitv_iptv_portal;
INSERT INTO squitv.schema_migrations (version) VALUES (7);

RESET ROLE;