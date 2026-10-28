-- Postgres-backed job queue for asynchronous saved-item processing (currently just
-- metadata extraction). No message broker: a scheduled worker polls this table using
-- "SELECT ... FOR UPDATE SKIP LOCKED" so that multiple application instances can run
-- the worker concurrently without double-processing the same job.
--
-- The unique (saved_item_id, job_type) constraint is what makes enqueueing idempotent:
-- the application inserts with ON CONFLICT DO NOTHING in the same transaction as the
-- saved_items insert, so a job always exists for an item and is never duplicated even
-- if save is retried.
CREATE TABLE processing_jobs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    saved_item_id    UUID NOT NULL REFERENCES saved_items (id) ON DELETE CASCADE,
    job_type         VARCHAR(50) NOT NULL,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_processing_jobs_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    attempt_count    INT NOT NULL DEFAULT 0,
    next_retry_at    TIMESTAMPTZ,
    last_error_code  VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_processing_jobs_item_type UNIQUE (saved_item_id, job_type)
);

CREATE INDEX idx_processing_jobs_poll ON processing_jobs (status, next_retry_at);