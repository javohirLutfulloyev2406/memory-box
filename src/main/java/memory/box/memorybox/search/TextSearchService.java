package memory.box.memorybox.search;

import memory.box.memorybox.saveditem.domain.SavedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Case-insensitive keyword search across title, description, user note and extracted content text. */
@Service
public class TextSearchService implements SearchService {

    private final SavedItemSearchRepository savedItemSearchRepository;

    public TextSearchService(SavedItemSearchRepository savedItemSearchRepository) {
        this.savedItemSearchRepository = savedItemSearchRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SavedItem> search(UUID userId, String query, Pageable pageable) {
        return savedItemSearchRepository.search(userId, query, pageable);
    }
}
