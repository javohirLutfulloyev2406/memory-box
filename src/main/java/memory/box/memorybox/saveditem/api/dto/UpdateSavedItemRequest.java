package memory.box.memorybox.saveditem.api.dto;

import jakarta.validation.constraints.Size;

/**
 * PATCH payload. A field left as {@code null} means "do not change this field" — this
 * endpoint cannot be used to clear title/userNote back to empty; that is an accepted
 * MVP limitation (see README).
 */
public record UpdateSavedItemRequest(
        @Size(max = 500, message = "title must be at most 500 characters")
        String title,

        @Size(max = 2000, message = "userNote must be at most 2000 characters")
        String userNote
) {
}
