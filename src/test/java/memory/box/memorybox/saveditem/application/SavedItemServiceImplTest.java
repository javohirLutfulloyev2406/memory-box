package memory.box.memorybox.saveditem.application;

import memory.box.memorybox.common.exception.ResourceNotFoundException;
import memory.box.memorybox.metadata.UrlNormalizer;
import memory.box.memorybox.metadata.UrlValidator;
import memory.box.memorybox.processing.ProcessingJobService;
import memory.box.memorybox.saveditem.api.dto.CreateSavedItemRequest;
import memory.box.memorybox.saveditem.api.dto.UpdateSavedItemRequest;
import memory.box.memorybox.saveditem.domain.SavedItem;
import memory.box.memorybox.saveditem.infrastructure.SavedItemRepository;
import memory.box.memorybox.user.User;
import memory.box.memorybox.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedItemServiceImplTest {

    @Mock
    private SavedItemRepository savedItemRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProcessingJobService processingJobService;
    @Mock
    private PlatformTransactionManager transactionManager;

    private SavedItemServiceImpl service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SavedItemServiceImpl(savedItemRepository, userRepository, new UrlValidator(), new UrlNormalizer(), processingJobService, transactionManager);
    }

    @Test
    void createsNewItemAndEnqueuesProcessingWhenNoDuplicateExists() {
        when(savedItemRepository.findByUserIdAndNormalizedUrl(userId, "https://example.com/a"))
                .thenReturn(Optional.empty());
        User user = new User("Test User");
        when(userRepository.getReferenceById(userId)).thenReturn(user);
        when(savedItemRepository.saveAndFlush(any(SavedItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SaveOutcome outcome = service.createOrGetItem(userId, new CreateSavedItemRequest("https://example.com/a", "note"));

        assertThat(outcome.created()).isTrue();
        assertThat(outcome.item().getNormalizedUrl()).isEqualTo("https://example.com/a");
        verify(processingJobService).enqueueMetadataFetch(outcome.item());
    }

    @Test
    void returnsExistingItemWhenConcurrentInsertLosesTheRace() {
        SavedItem existing = new SavedItem(new User("Test User"), "https://example.com/a", "https://example.com/a", null);
        when(savedItemRepository.findByUserIdAndNormalizedUrl(userId, "https://example.com/a"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existing));
        when(userRepository.getReferenceById(userId)).thenReturn(new User("Test User"));
        when(savedItemRepository.saveAndFlush(any(SavedItem.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        SaveOutcome outcome = service.createOrGetItem(userId, new CreateSavedItemRequest("https://example.com/a", null));

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.item()).isSameAs(existing);
        verify(processingJobService, never()).enqueueMetadataFetch(any());
    }

    @Test
    void returnsExistingItemAndSkipsEnqueueingWhenDuplicateExists() {
        SavedItem existing = new SavedItem(new User("Test User"), "https://example.com/a", "https://example.com/a", null);
        when(savedItemRepository.findByUserIdAndNormalizedUrl(userId, "https://example.com/a"))
                .thenReturn(Optional.of(existing));

        SaveOutcome outcome = service.createOrGetItem(userId, new CreateSavedItemRequest("https://example.com/a", null));

        assertThat(outcome.created()).isFalse();
        assertThat(outcome.item()).isSameAs(existing);
        verify(processingJobService, never()).enqueueMetadataFetch(any());
        verify(savedItemRepository, never()).save(any());
    }

    @Test
    void getItemThrowsNotFoundWhenItemDoesNotBelongToCaller() {
        UUID itemId = UUID.randomUUID();
        when(savedItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getItem(userId, itemId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateItemOnlyChangesProvidedFields() {
        UUID itemId = UUID.randomUUID();
        SavedItem item = new SavedItem(new User("Test User"), "https://example.com/a", "https://example.com/a", "original note");
        when(savedItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));

        SavedItem updated = service.updateItem(userId, itemId, new UpdateSavedItemRequest("New Title", null));

        assertThat(updated.getTitle()).isEqualTo("New Title");
        assertThat(updated.getUserNote()).isEqualTo("original note");
    }

    @Test
    void deleteItemRemovesOwnedItem() {
        UUID itemId = UUID.randomUUID();
        SavedItem item = new SavedItem(new User("Test User"), "https://example.com/a", "https://example.com/a", null);
        when(savedItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.of(item));

        service.deleteItem(userId, itemId);

        verify(savedItemRepository).delete(item);
    }

    @Test
    void deleteItemThrowsNotFoundForUnownedItem() {
        UUID itemId = UUID.randomUUID();
        when(savedItemRepository.findByIdAndUserId(itemId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteItem(userId, itemId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(savedItemRepository, never()).delete(any());
    }

    @Test
    void createRejectsInvalidUrlBeforeTouchingRepositories() {
        assertThatThrownBy(() -> service.createOrGetItem(userId, new CreateSavedItemRequest("not-a-url", null)))
                .isInstanceOf(memory.box.memorybox.common.exception.InvalidUrlException.class);
        verify(savedItemRepository, never()).findByUserIdAndNormalizedUrl(any(), any());
    }
}
