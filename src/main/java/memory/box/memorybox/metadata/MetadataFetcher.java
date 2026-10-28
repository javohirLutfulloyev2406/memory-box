package memory.box.memorybox.metadata;

import java.util.Optional;

/**
 * Fetches a public page's title/description for display. Implementations must never
 * throw for "the destination failed or wasn't fetchable" — that is a normal outcome
 * (empty result), not an exceptional one; saving a link must succeed independently of
 * whether this succeeds.
 */
public interface MetadataFetcher {

    Optional<FetchedMetadata> fetch(String url);
}
