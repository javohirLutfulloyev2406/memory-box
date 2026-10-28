package memory.box.memorybox.search;

import jakarta.validation.constraints.NotBlank;
import memory.box.memorybox.common.dto.PageResponse;
import memory.box.memorybox.common.security.CurrentUserProvider;
import memory.box.memorybox.saveditem.api.dto.SavedItemResponse;
import memory.box.memorybox.saveditem.application.SavedItemMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/search")
@Validated
public class SearchController {

    private final SearchService searchService;
    private final SavedItemMapper mapper;
    private final CurrentUserProvider currentUserProvider;

    public SearchController(SearchService searchService, SavedItemMapper mapper, CurrentUserProvider currentUserProvider) {
        this.searchService = searchService;
        this.mapper = mapper;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public PageResponse<SavedItemResponse> search(
            @RequestParam @NotBlank(message = "q must not be blank") String q,
            @PageableDefault(size = 20) Pageable pageable) {
        UUID userId = currentUserProvider.currentUserId();
        return PageResponse.from(searchService.search(userId, q, pageable), mapper::toResponse);
    }
}
