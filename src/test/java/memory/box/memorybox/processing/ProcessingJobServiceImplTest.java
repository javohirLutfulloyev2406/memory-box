package memory.box.memorybox.processing;

import memory.box.memorybox.metadata.FetchedMetadata;
import memory.box.memorybox.saveditem.domain.SavedItem;
import memory.box.memorybox.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceImplTest {

    @Mock
    private ProcessingJobRepository processingJobRepository;

    private ProcessingWorkerProperties workerProperties;
    private ProcessingJobServiceImpl service;

    @BeforeEach
    void setUp() {
        workerProperties = new ProcessingWorkerProperties();
        workerProperties.setMaxAttempts(3);
        workerProperties.setRetryBackoffSeconds(30);
        service = new ProcessingJobServiceImpl(processingJobRepository, workerProperties);
    }

    @Test
    void enqueueDelegatesToIdempotentNativeInsert() {
        SavedItem item = newItem();
        setId(item, UUID.randomUUID());

        service.enqueueMetadataFetch(item);

        org.mockito.Mockito.verify(processingJobRepository).enqueue(item.getId(), JobType.METADATA_FETCH.name());
    }

    @Test
    void recordSuccessMarksJobAndItemCompleted() {
        ProcessingJob job = newJob();
        when(processingJobRepository.findWithSavedItemById(job.getId())).thenReturn(Optional.of(job));

        service.recordSuccess(job.getId(), Optional.of(new FetchedMetadata("A Title", "A description")));

        assertThat(job.getStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(job.getSavedItem().getProcessingStatus()).isEqualTo(ProcessingStatus.COMPLETED);
        assertThat(job.getSavedItem().getTitle()).isEqualTo("A Title");
    }

    @Test
    void recordFailureSchedulesBackoffRetryWhenAttemptsRemain() {
        ProcessingJob job = newJob();
        when(processingJobRepository.findWithSavedItemById(job.getId())).thenReturn(Optional.of(job));

        service.recordFailure(job.getId(), "METADATA_FETCH_FAILED");

        assertThat(job.getAttemptCount()).isEqualTo(1);
        assertThat(job.getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(job.getNextRetryAt()).isAfter(Instant.now());
        // Still retrying, so the item itself hasn't given up yet.
        assertThat(job.getSavedItem().getProcessingStatus()).isEqualTo(ProcessingStatus.PENDING);
    }

    @Test
    void recordFailurePermanentlyFailsOnceAttemptsExhausted() {
        ProcessingJob job = newJob();
        job.setAttemptCount(2); // next failure is the 3rd, == maxAttempts
        when(processingJobRepository.findWithSavedItemById(job.getId())).thenReturn(Optional.of(job));

        service.recordFailure(job.getId(), "METADATA_FETCH_FAILED");

        assertThat(job.getAttemptCount()).isEqualTo(3);
        assertThat(job.getStatus()).isEqualTo(ProcessingStatus.FAILED);
        assertThat(job.getNextRetryAt()).isNull();
        assertThat(job.getSavedItem().getProcessingStatus()).isEqualTo(ProcessingStatus.FAILED);
    }

    private SavedItem newItem() {
        return new SavedItem(new User("Test User"), "https://example.com/a", "https://example.com/a", null);
    }

    private ProcessingJob newJob() {
        SavedItem item = newItem();
        setId(item, UUID.randomUUID());
        ProcessingJob job = new ProcessingJob();
        setField(job, "id", UUID.randomUUID());
        setField(job, "savedItem", item);
        setField(job, "jobType", JobType.METADATA_FETCH);
        setField(job, "status", ProcessingStatus.PROCESSING);
        setField(job, "attemptCount", 0);
        return job;
    }

    private void setId(SavedItem item, UUID id) {
        setField(item, "id", id);
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
