package memory.box.memorybox.saveditem.api.dto;

import memory.box.memorybox.processing.ProcessingStatus;

import java.time.Instant;
import java.util.UUID;

public record SavedItemResponse(
        UUID id,
        String url,
        String normalizedUrl,
        String title,
        String description,
        String source,
        String contentType,
        String userNote,
        Instant savedAt,
        Instant publishedAt,
        ProcessingStatus processingStatus,
        Instant createdAt,
        Instant updatedAt
) {
}
