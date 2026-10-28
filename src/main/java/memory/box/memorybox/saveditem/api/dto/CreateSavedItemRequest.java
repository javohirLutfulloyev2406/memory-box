package memory.box.memorybox.saveditem.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSavedItemRequest(
        @NotBlank(message = "url must not be blank")
        @Size(max = 2048, message = "url must be at most 2048 characters")
        String url,

        @Size(max = 2000, message = "userNote must be at most 2000 characters")
        String userNote
) {
}
