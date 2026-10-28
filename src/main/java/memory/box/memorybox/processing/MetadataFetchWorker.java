package memory.box.memorybox.processing;

import memory.box.memorybox.metadata.FetchedMetadata;
import memory.box.memorybox.metadata.MetadataFetcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Polls {@code processing_jobs} for runnable {@code METADATA_FETCH} jobs and runs
 * them. Claiming (see {@link ProcessingJobService#claimBatch()}) and per-job outcome
 * recording are separate, short transactions on purpose: the potentially slow network
 * call to {@link MetadataFetcher} must never happen while holding a DB transaction
 * open.
 */
@Component
public class MetadataFetchWorker {

    private static final Logger log = LoggerFactory.getLogger(MetadataFetchWorker.class);

    private final ProcessingJobService processingJobService;
    private final MetadataFetcher metadataFetcher;
    private final ProcessingWorkerProperties properties;

    public MetadataFetchWorker(ProcessingJobService processingJobService,
                                MetadataFetcher metadataFetcher,
                                ProcessingWorkerProperties properties) {
        this.processingJobService = processingJobService;
        this.metadataFetcher = metadataFetcher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${app.processing.worker.poll-interval-ms:10000}")
    public void pollAndProcess() {
        if (!properties.isEnabled()) {
            return;
        }
        List<ProcessingJob> claimed = processingJobService.claimBatch();
        for (ProcessingJob job : claimed) {
            processOne(job);
        }
    }

    private void processOne(ProcessingJob job) {
        try {
            Optional<FetchedMetadata> metadata = metadataFetcher.fetch(job.getSavedItem().getUrl());
            if (metadata.isPresent()) {
                processingJobService.recordSuccess(job.getId(), metadata);
            } else {
                processingJobService.recordFailure(job.getId(), "METADATA_FETCH_FAILED");
            }
        } catch (Exception ex) {
            // Never let one bad job crash the scheduler loop, and never surface the
            // raw exception beyond a generic error code.
            log.warn("Unexpected error processing job {}: {}", job.getId(), ex.getClass().getSimpleName());
            processingJobService.recordFailure(job.getId(), "METADATA_FETCH_UNEXPECTED_ERROR");
        }
    }
}
