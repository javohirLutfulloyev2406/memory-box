package memory.box.memorybox.processing;

import memory.box.memorybox.metadata.FetchedMetadata;
import memory.box.memorybox.saveditem.domain.SavedItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ProcessingJobServiceImpl implements ProcessingJobService {

    private final ProcessingJobRepository processingJobRepository;
    private final ProcessingWorkerProperties workerProperties;

    public ProcessingJobServiceImpl(ProcessingJobRepository processingJobRepository,
                                     ProcessingWorkerProperties workerProperties) {
        this.processingJobRepository = processingJobRepository;
        this.workerProperties = workerProperties;
    }

    @Override
    public void enqueueMetadataFetch(SavedItem item) {
        processingJobRepository.enqueue(item.getId(), JobType.METADATA_FETCH.name());
    }

    @Override
    @Transactional
    public List<ProcessingJob> claimBatch() {
        List<java.util.UUID> claimedIds = processingJobRepository.claimRunnableJobIds(
                workerProperties.getMaxAttempts(), Instant.now(), workerProperties.getBatchSize());
        if (claimedIds.isEmpty()) {
            return List.of();
        }
        List<ProcessingJob> jobs = processingJobRepository.findAllWithSavedItemByIdIn(claimedIds);
        for (ProcessingJob job : jobs) {
            job.getSavedItem().markProcessingStatus(ProcessingStatus.PROCESSING);
        }
        return jobs;
    }

    @Override
    @Transactional
    public void recordSuccess(UUID jobId, Optional<FetchedMetadata> metadata) {
        ProcessingJob job = requireJob(jobId);
        SavedItem item = job.getSavedItem();
        metadata.ifPresent(m -> item.applyMetadata(m.title(), m.description()));
        item.markProcessingStatus(ProcessingStatus.COMPLETED);
        job.setStatus(ProcessingStatus.COMPLETED);
        job.setLastErrorCode(null);
    }

    @Override
    @Transactional
    public void recordFailure(UUID jobId, String errorCode) {
        ProcessingJob job = requireJob(jobId);
        int attempts = job.getAttemptCount() + 1;
        job.setAttemptCount(attempts);
        job.setLastErrorCode(errorCode);
        job.setStatus(ProcessingStatus.FAILED);

        SavedItem item = job.getSavedItem();
        if (attempts >= workerProperties.getMaxAttempts()) {
            job.setNextRetryAt(null);
            item.markProcessingStatus(ProcessingStatus.FAILED);
        } else {
            long backoffSeconds = (long) workerProperties.getRetryBackoffSeconds() * attempts;
            job.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
            // A retry is still scheduled, so from the item's point of view processing
            // has not given up yet.
            item.markProcessingStatus(ProcessingStatus.PENDING);
        }
    }

    private ProcessingJob requireJob(UUID jobId) {
        return processingJobRepository.findWithSavedItemById(jobId)
                .orElseThrow(() -> new IllegalStateException("Processing job disappeared while being processed: " + jobId));
    }
}
