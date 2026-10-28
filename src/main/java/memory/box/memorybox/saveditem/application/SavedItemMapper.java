package memory.box.memorybox.saveditem.application;

import memory.box.memorybox.saveditem.api.dto.SavedItemResponse;
import memory.box.memorybox.saveditem.domain.SavedItem;
import org.springframework.stereotype.Component;

@Component
public class SavedItemMapper {

    public SavedItemResponse toResponse(SavedItem item) {
        return new SavedItemResponse(
                item.getId(),
                item.getUrl(),
                item.getNormalizedUrl(),
                item.getTitle(),
                item.getDescription(),
                item.getSource(),
                item.getContentType(),
                item.getUserNote(),
                item.getSavedAt(),
                item.getPublishedAt(),
                item.getProcessingStatus(),
                item.getCreatedAt(),
                item.getUpdatedAt());
    }
}
