package memory.box.memorybox.search;

import memory.box.memorybox.saveditem.domain.SavedItem;

import java.util.List;
import java.util.UUID;

/**
 * Not implemented yet — this is the contract the future semantic-search phase will
 * fulfill. It is intentionally not a Spring bean: nothing in the application depends
 * on it today, so the app starts and runs with zero AI/embedding infrastructure.
 *
 * <p><strong>Planned implementation (hybrid search):</strong>
 * <ol>
 *   <li>Add a {@code pgvector} extension and an {@code embedding vector(N)} column
 *       (plus an {@code embeddings} table if multiple chunks per item are needed) to
 *       {@code saved_items}, via a new Flyway migration — not added now per the
 *       instruction to introduce embeddings/tags only once implemented.</li>
 *   <li>{@link #embed} calls an embedding model (e.g. via an HTTP client to an
 *       embeddings API) to turn the save's title/description/content/user note into a
 *       vector, written by a new {@code processing.JobType} (e.g.
 *       {@code EMBEDDING_GENERATION}) through the same job-queue mechanism
 *       {@code ProcessingJobService} already provides for metadata extraction.</li>
 *   <li>{@link #search} runs an ANN similarity query (e.g. {@code <=>} cosine distance
 *       with an HNSW/IVFFlat index) scoped to the user, in parallel with
 *       {@link TextSearchService}'s full-text query.</li>
 *   <li>A hybrid ranker (e.g. weighted sum or reciprocal rank fusion of the full-text
 *       {@code ts_rank} and vector similarity scores) merges the two result sets. This
 *       is the point where {@link SearchService} gets a {@code HybridSearchService}
 *       implementation composing both, behind the same unchanged controller.</li>
 * </ol>
 * None of this is required for keyword search to work today; it only changes how
 * {@code GET /api/v1/search} answers once implemented, not its contract.
 */
public interface EmbeddingSearchService {

    float[] embed(String text);

    List<SavedItem> search(UUID userId, float[] queryEmbedding, int limit);
}
