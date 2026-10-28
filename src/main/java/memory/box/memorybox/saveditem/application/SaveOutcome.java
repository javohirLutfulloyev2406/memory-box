package memory.box.memorybox.saveditem.application;

import memory.box.memorybox.saveditem.domain.SavedItem;

/** {@code created=false} means an item with the same normalized URL already existed and was returned as-is. */
public record SaveOutcome(SavedItem item, boolean created) {
}
