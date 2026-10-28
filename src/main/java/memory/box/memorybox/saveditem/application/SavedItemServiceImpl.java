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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.util.UUID;

@Service
public class SavedItemServiceImpl implements SavedItemService {

    private final SavedItemRepository savedItemRepository;
    private final UserRepository userRepository;
    private final UrlValidator urlValidator;
    private final UrlNormalizer urlNormalizer;
    private final ProcessingJobService processingJobService;
    private final TransactionTemplate transactionTemplate;

    public SavedItemServiceImpl(SavedItemRepository savedItemRepository,
                                 UserRepository userRepository,
                                 UrlValidator urlValidator,
                                 UrlNormalizer urlNormalizer,
                                 ProcessingJobService processingJobService,
                                 PlatformTransactionManager transactionManager) {
        this.savedItemRepository = savedItemRepository;
        this.userRepository = userRepository;
        this.urlValidator = urlValidator;
        this.urlNormalizer = urlNormalizer;
        this.processingJobService = processingJobService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public SaveOutcome createOrGetItem(UUID userId, CreateSavedItemRequest request) {
        URI uri = urlValidator.validate(request.url());
        String normalizedUrl = urlNormalizer.normalize(uri);

        // The check-then-insert below runs in its own explicit transaction (via
        // TransactionTemplate, not @Transactional) so that if the insert loses a race to a
        // concurrent request for the same (user, normalized URL), only *that* transaction
        // rolls back. Catching the exception here, inside the still-open @Transactional
        // method it was thrown in, would leave the surrounding transaction marked
        // rollback-only by Hibernate, and returning normally afterward would fail at commit
        // with UnexpectedRollbackException instead of succeeding.
        try {
            return transactionTemplate.execute(status ->
                    savedItemRepository.findByUserIdAndNormalizedUrl(userId, normalizedUrl)
                            .map(existing -> new SaveOutcome(existing, false))
                            .orElseGet(() -> createNewItem(userId, request, normalizedUrl)));
        } catch (DataIntegrityViolationException raceLostToConcurrentInsert) {
            // The first transaction already rolled back; this read runs in a fresh one.
            // Another request for the same (user, normalized URL) committed between our
            // SELECT and our INSERT, and the unique constraint caught it. Return that row
            // instead of surfacing a 409, per the idempotent-save contract documented on the
            // V2 migration.
            return savedItemRepository.findByUserIdAndNormalizedUrl(userId, normalizedUrl)
                    .map(existing -> new SaveOutcome(existing, false))
                    .orElseThrow(() -> raceLostToConcurrentInsert);
        }
    }

    private SaveOutcome createNewItem(UUID userId, CreateSavedItemRequest request, String normalizedUrl) {
        User user = userRepository.getReferenceById(userId);
        SavedItem item = new SavedItem(user, request.url(), normalizedUrl, request.userNote());
        // Flush (rather than save()) so a unique-constraint violation from a concurrent
        // insert of the same (user, normalized URL) surfaces here, inside the try/catch
        // in createOrGetItem, instead of silently waiting for a later auto-flush.
        SavedItem saved = savedItemRepository.saveAndFlush(item);
        // Same transaction as the insert above: either both the item and its
        // processing job exist, or (on rollback) neither does.
        processingJobService.enqueueMetadataFetch(saved);
        return new SaveOutcome(saved, true);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SavedItem> listItems(UUID userId, Pageable pageable) {
        return savedItemRepository.findByUserId(userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public SavedItem getItem(UUID userId, UUID itemId) {
        return findOwnedOrThrow(userId, itemId);
    }

    @Override
    @Transactional
    public SavedItem updateItem(UUID userId, UUID itemId, UpdateSavedItemRequest request) {
        SavedItem item = findOwnedOrThrow(userId, itemId);
        if (request.title() != null) {
            item.setTitle(request.title());
        }
        if (request.userNote() != null) {
            item.setUserNote(request.userNote());
        }
        return item;
    }

    @Override
    @Transactional
    public void deleteItem(UUID userId, UUID itemId) {
        SavedItem item = findOwnedOrThrow(userId, itemId);
        savedItemRepository.delete(item);
    }

    private SavedItem findOwnedOrThrow(UUID userId, UUID itemId) {
        return savedItemRepository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Saved item not found: " + itemId));
    }
}
