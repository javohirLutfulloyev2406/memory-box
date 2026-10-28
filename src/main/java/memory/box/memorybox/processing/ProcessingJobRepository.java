package memory.box.memorybox.processing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, UUID> {

    /**
     * Idempotent enqueue: a no-op if a job of this type already exists for the item.
     * Relies on the {@code uq_processing_jobs_item_type} unique constraint.
     */
    @Modifying
    @Query(value = """
            INSERT INTO processing_jobs (saved_item_id, job_type, status, attempt_count, created_at, updated_at)
            VALUES (:savedItemId, :jobType, 'PENDING', 0, now(), now())
            ON CONFLICT (saved_item_id, job_type) DO NOTHING
            """, nativeQuery = true)
    void enqueue(@Param("savedItemId") UUID savedItemId, @Param("jobType") String jobType);

    /**
     * Atomically claims up to {@code limit} runnable jobs (PENDING, or FAILED with a
     * due retry and attempts remaining) for this worker and marks them PROCESSING, so
     * two worker instances polling concurrently never pick up the same row. FOR UPDATE
     * SKIP LOCKED is what makes that safe without a distributed lock.
     */
    // Deliberately NOT @Modifying: Hibernate would call executeUpdate(), which cannot
    // return rows. This runs as a plain query so the PostgreSQL JDBC driver returns
    // the RETURNING id result set, giving us both the atomic claim and the id list in
    // one round trip.
    @Query(value = """
            UPDATE processing_jobs
            SET status = 'PROCESSING', updated_at = now()
            WHERE id IN (
                SELECT id FROM processing_jobs
                WHERE (status = 'PENDING')
                   OR (status = 'FAILED' AND attempt_count < :maxAttempts AND next_retry_at <= :now)
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING id
            """, nativeQuery = true)
    List<UUID> claimRunnableJobIds(@Param("maxAttempts") int maxAttempts, @Param("now") Instant now, @Param("limit") int limit);

    /** Loads claimed jobs with their {@code savedItem} eagerly fetched so it is safe to read after this transaction commits. */
    @Query("SELECT j FROM ProcessingJob j JOIN FETCH j.savedItem WHERE j.id IN :ids")
    List<ProcessingJob> findAllWithSavedItemByIdIn(@Param("ids") List<UUID> ids);

    @Query("SELECT j FROM ProcessingJob j JOIN FETCH j.savedItem WHERE j.id = :id")
    java.util.Optional<ProcessingJob> findWithSavedItemById(@Param("id") UUID id);
}
