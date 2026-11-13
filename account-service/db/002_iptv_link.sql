SET LOCAL ROLE squitv_account_owner;

GRANT USAGE ON SCHEMA squitv TO squitv_iptv_portal;
GRANT SELECT (id, issuer, subject), INSERT (issuer, subject), UPDATE (subject)
    ON squitv.accounts TO squitv_iptv_portal;
GRANT SELECT (account_id, encrypted_credentials, credential_key_version),
    INSERT (account_id, provider_key, encrypted_credentials, credential_key_version),
    UPDATE (encrypted_credentials, credential_key_version)
    ON squitv.provider_links TO squitv_iptv_portal;

INSERT INTO squitv.schema_migrations (version) VALUES (2) ON CONFLICT DO NOTHING;

RESET ROLE;