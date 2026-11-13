SET LOCAL ROLE squitv_account_owner;

ALTER TABLE squitv.provider_links
    ADD COLUMN IF NOT EXISTS validated_at timestamptz,
    ADD COLUMN IF NOT EXISTS provider_expires_at timestamptz;

GRANT SELECT (validated_at, provider_expires_at),
    INSERT (validated_at, provider_expires_at), UPDATE (validated_at, provider_expires_at)
    ON squitv.provider_links TO squitv_iptv_portal;

INSERT INTO squitv.schema_migrations (version) VALUES (3) ON CONFLICT DO NOTHING;

RESET ROLE;