-- A saved_item is one link a user asked Memory Box to remember.
--
-- Duplicate handling decision: a (user_id, normalized_url) unique constraint makes
-- saving the same link twice idempotent at the database level. The application layer
-- catches the constraint violation and returns the existing row instead of an error,
-- so "save this link" is safe to repeat (e.g. re-sending the same Telegram message).
--
-- processing_status mirrors the lifecycle of the associated metadata-extraction job
-- and is denormalized onto the item so list/search queries don't need a join to know
-- whether metadata is ready.
CREATE TABLE saved_items (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    url                TEXT NOT NULL,
    normalized_url     TEXT NOT NULL,
    title              VARCHAR(500),
    description        TEXT,
    content_text       TEXT,
    source             VARCHAR(100),
    content_type       VARCHAR(50),
    user_note          TEXT,
    saved_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at       TIMESTAMPTZ,
    processing_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_saved_items_processing_status
        CHECK (processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_saved_items_user_normalized_url UNIQUE (user_id, normalized_url)
);

CREATE INDEX idx_saved_items_user_id ON saved_items (user_id);
CREATE INDEX idx_saved_items_saved_at ON saved_items (saved_at DESC);
CREATE INDEX idx_saved_items_user_saved_at ON saved_items (user_id, saved_at DESC);

-- Full-text search preparation: a generated tsvector column over the fields a user's
-- natural-language query is most likely to match, indexed with GIN. to_tsvector with an
-- explicit regconfig argument is IMMUTABLE, so it is usable in a generated column.
-- This is phase one of the hybrid search plan described in the README: today this
-- powers plain keyword search; later a pgvector embedding column can be added
-- alongside it and the two combined (e.g. reciprocal rank fusion) without touching
-- this column.
ALTER TABLE saved_items ADD COLUMN search_vector tsvector
    GENERATED ALWAYS AS (
        setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
        setweight(to_tsvector('english', coalesce(description, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(user_note, '')), 'B') ||
        setweight(to_tsvector('english', coalesce(content_text, '')), 'C')
    ) STORED;

CREATE INDEX idx_saved_items_search_vector ON saved_items USING GIN (search_vector);