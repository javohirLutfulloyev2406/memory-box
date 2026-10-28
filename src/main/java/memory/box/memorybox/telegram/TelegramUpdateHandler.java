package memory.box.memorybox.telegram;

import memory.box.memorybox.auth.IdentityResolutionService;
import memory.box.memorybox.common.exception.InvalidUrlException;
import memory.box.memorybox.saveditem.api.dto.CreateSavedItemRequest;
import memory.box.memorybox.saveditem.application.SaveOutcome;
import memory.box.memorybox.saveditem.application.SavedItemService;
import memory.box.memorybox.telegram.dto.TelegramMessage;
import memory.box.memorybox.telegram.dto.TelegramUpdate;
import memory.box.memorybox.user.IdentityProvider;
import memory.box.memorybox.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The basic Telegram flow end to end: pull a URL out of an incoming message, validate
 * it, resolve the sender to a Memory Box user (creating one on first contact), save
 * the link, and reply with a short status message.
 */
@Service
public class TelegramUpdateHandler {

    private static final Logger log = LoggerFactory.getLogger(TelegramUpdateHandler.class);
    private static final Pattern URL_PATTERN = Pattern.compile("(?i)\\bhttps?://\\S+");

    private final IdentityResolutionService identityResolutionService;
    private final SavedItemService savedItemService;
    private final TelegramClient telegramClient;

    public TelegramUpdateHandler(IdentityResolutionService identityResolutionService,
                                  SavedItemService savedItemService,
                                  TelegramClient telegramClient) {
        this.identityResolutionService = identityResolutionService;
        this.savedItemService = savedItemService;
        this.telegramClient = telegramClient;
    }

    public void handle(TelegramUpdate update) {
        TelegramMessage message = update.message();
        if (message == null || message.chat() == null || message.chat().id() == null) {
            return;
        }
        long chatId = message.chat().id();

        if (message.from() == null || message.from().id() == null) {
            telegramClient.sendMessage(chatId, "Sorry, I couldn't identify you. Please try again.");
            return;
        }
        if (message.text() == null || message.text().isBlank()) {
            telegramClient.sendMessage(chatId, "Send me a link and I'll save it for you.");
            return;
        }

        String url = extractUrl(message.text());
        if (url == null) {
            telegramClient.sendMessage(chatId, "I couldn't find a link in that message. Send me a URL to save it.");
            return;
        }

        try {
            User user = identityResolutionService.resolveOrCreateUser(
                    IdentityProvider.TELEGRAM, String.valueOf(message.from().id()), null);
            SaveOutcome outcome = savedItemService.createOrGetItem(user.getId(), new CreateSavedItemRequest(url, null));
            String reply = outcome.created()
                    ? "Saved! I'll fetch the title shortly."
                    : "You already saved this link.";
            telegramClient.sendMessage(chatId, reply);
        } catch (InvalidUrlException ex) {
            telegramClient.sendMessage(chatId, "That link doesn't look valid: " + ex.getMessage());
        } catch (Exception ex) {
            log.warn("Failed to save a link from Telegram chat {}: {}", chatId, ex.getClass().getSimpleName());
            telegramClient.sendMessage(chatId, "Sorry, something went wrong saving that link. Please try again.");
        }
    }

    private String extractUrl(String text) {
        Matcher matcher = URL_PATTERN.matcher(text);
        return matcher.find() ? matcher.group() : null;
    }
}
