package memory.box.memorybox.saveditem.api;

import jakarta.validation.Valid;
import memory.box.memorybox.common.dto.PageResponse;
import memory.box.memorybox.common.security.CurrentUserProvider;
import memory.box.memorybox.saveditem.api.dto.CreateSavedItemRequest;
import memory.box.memorybox.saveditem.api.dto.SavedItemResponse;
import memory.box.memorybox.saveditem.api.dto.UpdateSavedItemRequest;
import memory.box.memorybox.saveditem.application.SaveOutcome;
import memory.box.memorybox.saveditem.application.SavedItemMapper;
import memory.box.memorybox.saveditem.application.SavedItemService;
import memory.box.memorybox.saveditem.domain.SavedItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Thin HTTP layer: all business rules and ownership checks live in {@link SavedItemService}. */
@RestController
@RequestMapping("/api/v1/items")
public class SavedItemController {

    private final SavedItemService savedItemService;
    private final SavedItemMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public SavedItemController(SavedItemService savedItemService, SavedItemMapper mapper, CurrentUserProvider currentUserProvider) {
        this.savedItemService = savedItemService;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public ResponseEntity<SavedItemResponse> createItem(@Valid @RequestBody CreateSavedItemRequest request) {
        UUID userId = currentUserProvider.currentUserId();
        SaveOutcome outcome = savedItemService.createOrGetItem(userId, request);
        HttpStatus status = outcome.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(mapper.toResponse(outcome.item()));
    }

    @GetMapping
    public PageResponse<SavedItemResponse> listItems(
            @PageableDefault(size = 20) @org.springframework.data.web.SortDefault(sort = "savedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        UUID userId = currentUserProvider.currentUserId();
        return PageResponse.from(savedItemService.listItems(userId, pageable), mapper::toResponse);
    }

    @GetMapping("/{id}")
    public SavedItemResponse getItem(@PathVariable UUID id) {
        UUID userId = currentUserProvider.currentUserId();
        SavedItem item = savedItemService.getItem(userId, id);
        return mapper.toResponse(item);
    }

    @PatchMapping("/{id}")
    public SavedItemResponse updateItem(@PathVariable UUID id, @Valid @RequestBody UpdateSavedItemRequest request) {
        UUID userId = currentUserProvider.currentUserId();
        SavedItem item = savedItemService.updateItem(userId, id, request);
        return mapper.toResponse(item);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable UUID id) {
        UUID userId = currentUserProvider.currentUserId();
        savedItemService.deleteItem(userId, id);
        return ResponseEntity.noContent().build();
    }
}
