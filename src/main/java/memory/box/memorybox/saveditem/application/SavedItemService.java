package memory.box.memorybox.saveditem.application;

import memory.box.memorybox.saveditem.api.dto.CreateSavedItemRequest;
import memory.box.memorybox.saveditem.api.dto.UpdateSavedItemRequest;
import memory.box.memorybox.saveditem.domain.SavedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Saved-item business rules. Every method is scoped to a {@code userId} supplied by
 * the caller from the authenticated principal (never from request input) — this is
 * the ownership boundary for the whole feature.
 */
public interface SavedItemService {

    SaveOutcome createOrGetItem(UUID userId, CreateSavedItemRequest request);

    Page<SavedItem> listItems(UUID userId, Pageable pageable);

    SavedItem getItem(UUID userId, UUID itemId);

    SavedItem updateItem(UUID userId, UUID itemId, UpdateSavedItemRequest request);

    void deleteItem(UUID userId, UUID itemId);
}
