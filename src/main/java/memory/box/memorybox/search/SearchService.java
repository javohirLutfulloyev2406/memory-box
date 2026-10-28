package memory.box.memorybox.search;

import memory.box.memorybox.saveditem.domain.SavedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Application-layer boundary for "find saved items matching a natural-language-ish
 * query." Today this is backed by Postgres full-text search
 * ({@link TextSearchService}). The API and controller never depend on that directly —
 * only on this interface — so a future hybrid implementation (full-text + pgvector
 * semantic similarity, see {@link EmbeddingSearchService}) can be swapped in, or
 * composed with this one, without changing {@code GET /api/v1/search}.
 */
public interface SearchService {

    Page<SavedItem> search(UUID userId, String query, Pageable pageable);
}
