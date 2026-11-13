SET LOCAL ROLE squitv_account_owner;

ALTER TABLE squitv.favorites ADD COLUMN title text CHECK (length(title) <= 240);

GRANT SELECT (id) ON squitv.provider_links TO squitv_iptv_portal;
GRANT SELECT, INSERT, UPDATE ON squitv.profiles, squitv.favorites TO squitv_iptv_portal;
GRANT USAGE ON SEQUENCE squitv.profile_revision TO squitv_iptv_portal;

CREATE INDEX favorites_revision_feed ON squitv.favorites (account_id, profile_id, link_id, revision);

INSERT INTO squitv.schema_migrations (version) VALUES (6);

RESET ROLE;