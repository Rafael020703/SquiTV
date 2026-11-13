SET LOCAL ROLE squitv_account_owner;

CREATE SEQUENCE squitv.catalog_metadata_revision;
ALTER TABLE squitv.catalog_index ADD COLUMN metadata_seeded_revision bigint NOT NULL DEFAULT 0;

CREATE TABLE squitv.catalog_metadata (
    account_id uuid NOT NULL,
    link_id uuid NOT NULL,
    kind text NOT NULL,
    item_id integer NOT NULL CHECK (item_id > 0),
    source_validated_at timestamptz,
    payload jsonb,
    updated_at timestamptz,
    revision bigint NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    priority boolean NOT NULL DEFAULT false,
    failures integer NOT NULL DEFAULT 0,
    PRIMARY KEY (account_id, link_id, kind, item_id),
    FOREIGN KEY (account_id, link_id, kind)
        REFERENCES squitv.catalog_index (account_id, link_id, kind) ON DELETE CASCADE
);

CREATE INDEX catalog_metadata_due ON squitv.catalog_metadata (priority DESC, next_attempt_at);
CREATE INDEX catalog_metadata_feed ON squitv.catalog_metadata (account_id, link_id, kind, revision)
    WHERE revision>0;
GRANT SELECT, INSERT, UPDATE, DELETE ON squitv.catalog_metadata TO squitv_iptv_portal;
GRANT USAGE ON SEQUENCE squitv.catalog_metadata_revision TO squitv_iptv_portal;
INSERT INTO squitv.schema_migrations (version) VALUES (11);

RESET ROLE;
