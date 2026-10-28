package memory.box.memorybox.processing;

import memory.box.memorybox.metadata.FetchedMetadata;
import memory.box.memorybox.saveditem.domain.SavedItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Boundary the rest of the app uses to queue and run background work for a saved item. */
public interface ProcessingJobService {

    /**
     * Enqueues metadata extraction for a newly-created item. Must be called in the
     * same transaction as the {@code saved_items} insert so the save and the enqueue
     * either both happen or neither does.
     */
    void enqueueMetadataFetch(SavedItem item);

    /**
     * Atomically claims up to a configured batch of runnable jobs, marking both the
     * job and its item PROCESSING, so this and any other worker instance never pick
     * up the same job twice.
     */
    List<ProcessingJob> claimBatch();

    /**
     * Records a successful metadata fetch: applies the result and marks the job
     * COMPLETED. Takes a job id (not the entity returned by {@link #claimBatch()},
     * which is detached by the time the caller gets to it) and re-loads a managed
     * instance inside its own transaction before mutating it.
     */
    void recordSuccess(UUID jobId, Optional<FetchedMetadata> metadata);

    /** Records a failed attempt: schedules a backoff retry, or marks the job permanently FAILED once attempts are exhausted. */
    void recordFailure(UUID jobId, String errorCode);
}
