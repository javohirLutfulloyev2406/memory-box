package memory.box.memorybox.search;

import memory.box.memorybox.saveditem.domain.SavedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

/**
 * Keyword search over a user's saved items via the {@code search_vector} generated
 * column (see {@code V2__create_saved_items.sql}). {@code plainto_tsquery} treats the
 * input as plain words (no tsquery operator syntax to worry about from user input);
 * results are ranked by {@code ts_rank}, ties broken by recency.
 */
public interface SavedItemSearchRepository extends Repository<SavedItem, UUID> {

    @Query(value = """
            SELECT * FROM saved_items
            WHERE user_id = :userId
              AND search_vector @@ plainto_tsquery('english', :query)
            ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC, saved_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM saved_items
            WHERE user_id = :userId
              AND search_vector @@ plainto_tsquery('english', :query)
            """,
            nativeQuery = true)
    Page<SavedItem> search(@Param("userId") UUID userId, @Param("query") String query, Pageable pageable);
}
