SET LOCAL ROLE squitv_account_owner;

CREATE TABLE squitv.catalog_index (
    account_id uuid NOT NULL,
    link_id uuid NOT NULL,
    kind text NOT NULL CHECK (kind IN ('live', 'movie', 'series')),
    revision bigint NOT NULL DEFAULT 0,
    refreshed_at timestamptz,
    source_validated_at timestamptz,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    failures integer NOT NULL DEFAULT 0 CHECK (failures >= 0),
    last_error text,
    categories jsonb NOT NULL DEFAULT '[]',
    items jsonb NOT NULL DEFAULT '[]',
    PRIMARY KEY (account_id, link_id, kind),
    FOREIGN KEY (account_id, link_id) REFERENCES squitv.provider_links (account_id, id) ON DELETE CASCADE
);

CREATE INDEX catalog_index_due ON squitv.catalog_index (next_attempt_at, kind);
GRANT SELECT (provider_key) ON squitv.provider_links TO squitv_iptv_portal;
GRANT SELECT, INSERT, UPDATE ON squitv.catalog_index TO squitv_iptv_portal;

INSERT INTO squitv.schema_migrations (version) VALUES (10);

RESET ROLE;
