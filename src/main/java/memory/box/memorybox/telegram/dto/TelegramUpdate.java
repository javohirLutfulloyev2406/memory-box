package memory.box.memorybox.telegram.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Only the subset of Telegram's Update payload Memory Box actually uses; everything else is ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdate(
        @JsonProperty("update_id") Long updateId,
        TelegramMessage message
) {
}
