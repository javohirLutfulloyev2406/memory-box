package memory.box.memorybox.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin wrapper over the Telegram Bot HTTP API — no SDK dependency, just the one call
 * Memory Box needs ({@code sendMessage}). The bot token is only ever used to build the
 * request URL and is never logged.
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);

    private final RestClient restClient;
    private final TelegramBotProperties properties;

    public TelegramClient(TelegramBotProperties properties) {
        this.properties = properties;
        this.restClient = RestClient.builder().baseUrl(properties.getApiBaseUrl()).build();
    }

    public void sendMessage(long chatId, String text) {
        if (!properties.isEnabled() || properties.getToken().isBlank()) {
            return;
        }
        try {
            restClient.post()
                    .uri("/bot" + properties.getToken() + "/sendMessage")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new SendMessageRequest(chatId, text))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            log.warn("Failed to send Telegram message to chat {}: {}", chatId, ex.getClass().getSimpleName());
        }
    }

    private record SendMessageRequest(@JsonProperty("chat_id") long chatId, String text) {
    }
}
