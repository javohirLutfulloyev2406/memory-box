package memory.box.memorybox.telegram;

import memory.box.memorybox.telegram.dto.TelegramUpdate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Telegram webhook endpoint. This path is deliberately excluded from the normal
 * Spring Security authentication boundary (see {@code SecurityConfig}) because its
 * caller is Telegram, not a Memory Box user — it authenticates the request itself by
 * checking the secret token Telegram echoes back in a header, which must be
 * configured on both sides (see README "Telegram bot setup").
 */
@RestController
@RequestMapping("/api/telegram")
public class TelegramWebhookController {

    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramBotProperties properties;
    private final TelegramUpdateHandler updateHandler;

    public TelegramWebhookController(TelegramBotProperties properties, TelegramUpdateHandler updateHandler) {
        this.properties = properties;
        this.updateHandler = updateHandler;
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader(value = SECRET_HEADER, required = false) String secretHeader,
            @RequestBody TelegramUpdate update) {
        if (!properties.isEnabled()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!isValidSecret(secretHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        updateHandler.handle(update);
        return ResponseEntity.ok().build();
    }

    private boolean isValidSecret(String provided) {
        String expected = properties.getWebhookSecret();
        // Require an explicitly configured secret; never accept requests just
        // because none was configured.
        return expected != null && !expected.isBlank() && expected.equals(provided);
    }
}
