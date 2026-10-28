-- Core user model. A "user" is a Memory Box identity-agnostic account; one or more
-- external identities (Telegram, future web login, etc.) can be linked to it via
-- user_identities. All timestamps are stored as TIMESTAMPTZ (UTC) per project convention.

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name  VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_identities (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider          VARCHAR(32) NOT NULL,
    provider_user_id  VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_user_identities_provider_external UNIQUE (provider, provider_user_id)
);

CREATE INDEX idx_user_identities_user_id ON user_identities (user_id);