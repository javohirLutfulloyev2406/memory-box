package memory.box.memorybox.processing;

/** Lifecycle shared by {@code saved_items.processing_status} and {@code processing_jobs.status}. */
public enum ProcessingStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}
