package memory.box.memorybox.saveditem.infrastructure;

import memory.box.memorybox.saveditem.domain.SavedItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SavedItemRepository extends JpaRepository<SavedItem, UUID> {

    Page<SavedItem> findByUserId(UUID userId, Pageable pageable);

    Optional<SavedItem> findByIdAndUserId(UUID id, UUID userId);

    Optional<SavedItem> findByUserIdAndNormalizedUrl(UUID userId, String normalizedUrl);
}
