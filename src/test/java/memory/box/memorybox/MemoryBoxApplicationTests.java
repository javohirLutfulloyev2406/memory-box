package memory.box.memorybox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the full application context (Flyway migrations, JPA, Security, Jackson,
 * etc.) actually wires up against a real Postgres, via Testcontainers rather than
 * whatever datasource happens to be configured in {@code application.properties}.
 * Requires Docker to be available.
 */
@SpringBootTest
@Testcontainers
class MemoryBoxApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void disableBackgroundWork(DynamicPropertyRegistry registry) {
        registry.add("app.processing.worker.enabled", () -> "false");
        registry.add("app.metadata-fetch.enabled", () -> "false");
        registry.add("telegram.bot.enabled", () -> "false");
    }

    @Test
    void contextLoads() {
    }

}
