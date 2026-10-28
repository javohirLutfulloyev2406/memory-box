package memory.box.memorybox.integration;

import memory.box.memorybox.saveditem.api.dto.CreateSavedItemRequest;
import memory.box.memorybox.saveditem.application.SaveOutcome;
import memory.box.memorybox.saveditem.application.SavedItemService;
import memory.box.memorybox.saveditem.domain.SavedItem;
import memory.box.memorybox.saveditem.infrastructure.SavedItemRepository;
import memory.box.memorybox.search.SearchService;
import memory.box.memorybox.user.User;
import memory.box.memorybox.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real Postgres-specific SQL this project depends on (the generated
 * {@code search_vector} column, the per-user unique-URL constraint) against a real
 * database via Testcontainers, since none of that can be meaningfully verified with
 * mocks. Requires Docker to be available.
 */
@SpringBootTest
@Testcontainers
class SavedItemIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void disableBackgroundWork(DynamicPropertyRegistry registry) {
        registry.add("app.processing.worker.enabled", () -> "false");
        registry.add("app.metadata-fetch.enabled", () -> "false");
        registry.add("telegram.bot.enabled", () -> "false");
    }

    @Autowired
    private SavedItemService savedItemService;
    @Autowired
    private SavedItemRepository savedItemRepository;
    @Autowired
    private SearchService searchService;
    @Autowired
    private UserRepository userRepository;

    private UUID userId;

    @BeforeEach
    void createUser() {
        User user = userRepository.save(new User("Integration Test User"));
        userId = user.getId();
    }

    @Test
    void savingTheSameUrlTwiceReturnsTheSameItemInstead() {
        SaveOutcome first = savedItemService.createOrGetItem(userId,
                new CreateSavedItemRequest("https://example.com/java-interview", "first note"));
        SaveOutcome second = savedItemService.createOrGetItem(userId,
                new CreateSavedItemRequest("https://example.com/java-interview", "second note"));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.item().getId()).isEqualTo(first.item().getId());
        assertThat(savedItemRepository.findByUserId(userId, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test
    void databaseRejectsDuplicateNormalizedUrlForSameUserEvenBypassingTheServiceCheck() {
        User user = userRepository.findById(userId).orElseThrow();
        savedItemRepository.save(new SavedItem(user, "https://example.com/x", "https://example.com/x", null));

        assertThatThrownBy(() -> {
            savedItemRepository.save(new SavedItem(user, "https://example.com/x", "https://example.com/x", null));
            savedItemRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUsersCanSaveTheSameUrlIndependently() {
        User otherUser = userRepository.save(new User("Other User"));

        SaveOutcome first = savedItemService.createOrGetItem(userId,
                new CreateSavedItemRequest("https://example.com/shared", null));
        SaveOutcome second = savedItemService.createOrGetItem(otherUser.getId(),
                new CreateSavedItemRequest("https://example.com/shared", null));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isTrue();
        assertThat(first.item().getId()).isNotEqualTo(second.item().getId());
    }

    @Test
    void searchFindsItemByKeywordInTitleAndIsScopedToTheOwner() {
        SaveOutcome outcome = savedItemService.createOrGetItem(userId,
                new CreateSavedItemRequest("https://example.com/spring-transactions", "Spring tx notes"));
        SavedItem item = outcome.item();
        item.setTitle("Understanding Spring Transactions");
        savedItemRepository.save(item);
        savedItemRepository.flush();

        User otherUser = userRepository.save(new User("Other User"));

        var resultsForOwner = searchService.search(userId, "transactions", PageRequest.of(0, 20));
        var resultsForOtherUser = searchService.search(otherUser.getId(), "transactions", PageRequest.of(0, 20));

        assertThat(resultsForOwner.getContent()).extracting(SavedItem::getId).contains(item.getId());
        assertThat(resultsForOtherUser.getContent()).isEmpty();
    }
}
