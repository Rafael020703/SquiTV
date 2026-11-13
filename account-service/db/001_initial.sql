CREATE SCHEMA squitv AUTHORIZATION squitv_account_owner;

SET LOCAL ROLE squitv_account_owner;

CREATE TABLE squitv.schema_migrations (
    version integer PRIMARY KEY CHECK (version > 0),
    applied_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE squitv.accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    issuer text NOT NULL CHECK (length(issuer) BETWEEN 1 AND 512),
    subject text NOT NULL CHECK (length(subject) BETWEEN 1 AND 255),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (issuer, subject)
);

CREATE TABLE squitv.provider_links (
    account_id uuid PRIMARY KEY REFERENCES squitv.accounts ON DELETE CASCADE,
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    provider_key text NOT NULL CHECK (provider_key = 'xtream-current'),
    encrypted_credentials bytea,
    credential_key_version integer,
    CHECK (
        (encrypted_credentials IS NULL AND credential_key_version IS NULL)
        OR (encrypted_credentials IS NOT NULL AND length(encrypted_credentials) > 0
            AND credential_key_version IS NOT NULL AND credential_key_version > 0)
    ),
    UNIQUE (account_id, id)
);

CREATE SEQUENCE squitv.profile_revision;

CREATE TABLE squitv.profiles (
    account_id uuid NOT NULL REFERENCES squitv.accounts ON DELETE CASCADE,
    id uuid NOT NULL DEFAULT gen_random_uuid(),
    display_name text NOT NULL CHECK (length(btrim(display_name)) BETWEEN 1 AND 80),
    avatar_key text CHECK (length(avatar_key) <= 128),
    is_child boolean NOT NULL DEFAULT false,
    revision bigint NOT NULL DEFAULT nextval('squitv.profile_revision'),
    deleted_at timestamptz,
    PRIMARY KEY (account_id, id)
);

CREATE TABLE squitv.favorites (
    account_id uuid NOT NULL,
    profile_id uuid NOT NULL,
    link_id uuid NOT NULL,
    content_kind text NOT NULL CHECK (content_kind IN ('live', 'movie', 'series')),
    content_id bigint NOT NULL CHECK (content_id > 0),
    revision bigint NOT NULL DEFAULT nextval('squitv.profile_revision'),
    deleted_at timestamptz,
    PRIMARY KEY (account_id, profile_id, link_id, content_kind, content_id),
    FOREIGN KEY (account_id, profile_id) REFERENCES squitv.profiles ON DELETE CASCADE,
    FOREIGN KEY (account_id, link_id) REFERENCES squitv.provider_links (account_id, id) ON DELETE CASCADE
);

CREATE TABLE squitv.watch_progress (
    account_id uuid NOT NULL,
    profile_id uuid NOT NULL,
    link_id uuid NOT NULL,
    content_kind text NOT NULL CHECK (content_kind IN ('movie', 'episode')),
    content_id bigint NOT NULL CHECK (content_id > 0),
    position_ms bigint NOT NULL CHECK (position_ms >= 0),
    duration_ms bigint CHECK (duration_ms > 0),
    completed boolean NOT NULL DEFAULT false,
    revision bigint NOT NULL DEFAULT nextval('squitv.profile_revision'),
    deleted_at timestamptz,
    CHECK (duration_ms IS NULL OR position_ms <= duration_ms),
    PRIMARY KEY (account_id, profile_id, link_id, content_kind, content_id),
    FOREIGN KEY (account_id, profile_id) REFERENCES squitv.profiles ON DELETE CASCADE,
    FOREIGN KEY (account_id, link_id) REFERENCES squitv.provider_links (account_id, id) ON DELETE CASCADE
);

CREATE TABLE squitv.category_preferences (
    account_id uuid NOT NULL,
    profile_id uuid NOT NULL,
    link_id uuid NOT NULL,
    catalog_kind text NOT NULL CHECK (catalog_kind IN ('live', 'movie', 'series')),
    category_mode text NOT NULL CHECK (category_mode IN ('provider', 'genre')),
    category_order text[] NOT NULL DEFAULT '{}',
    hidden_categories text[] NOT NULL DEFAULT '{}',
    revision bigint NOT NULL DEFAULT nextval('squitv.profile_revision'),
    CHECK (catalog_kind <> 'live' OR category_mode = 'provider'),
    PRIMARY KEY (account_id, profile_id, link_id, catalog_kind),
    FOREIGN KEY (account_id, profile_id) REFERENCES squitv.profiles ON DELETE CASCADE,
    FOREIGN KEY (account_id, link_id) REFERENCES squitv.provider_links (account_id, id) ON DELETE CASCADE
);

INSERT INTO squitv.schema_migrations (version) VALUES (1);

GRANT USAGE ON SCHEMA squitv TO squitv_account_api;
GRANT SELECT ON squitv.schema_migrations TO squitv_account_api;

RESET ROLE;
