package memory.box.memorybox.telegram;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the webhook's own authentication (the secret header), which is checked in the
 * controller itself rather than by Spring Security (see the class Javadoc on
 * {@link TelegramWebhookController}).
 */
@WebMvcTest(TelegramWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
class TelegramWebhookControllerTest {

    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";
    private static final String BODY = "{\"update_id\":1}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramBotProperties properties;
    @MockitoBean
    private TelegramUpdateHandler updateHandler;

    @Test
    void returnsNotFoundWhenBotIsDisabled() throws Exception {
        when(properties.isEnabled()).thenReturn(false);

        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SECRET_HEADER, "whatever")
                        .content(BODY))
                .andExpect(status().isNotFound());
        verifyNoInteractions(updateHandler);
    }

    @Test
    void rejectsRequestWithMissingSecretHeader() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getWebhookSecret()).thenReturn("correct-secret");

        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(updateHandler);
    }

    @Test
    void rejectsRequestWithWrongSecretHeader() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getWebhookSecret()).thenReturn("correct-secret");

        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SECRET_HEADER, "wrong-secret")
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(updateHandler);
    }

    @Test
    void neverAcceptsARequestWhenNoSecretIsConfigured() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getWebhookSecret()).thenReturn("");

        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SECRET_HEADER, "")
                        .content(BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(updateHandler);
    }

    @Test
    void acceptsAndDelegatesRequestWithCorrectSecret() throws Exception {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getWebhookSecret()).thenReturn("correct-secret");

        mockMvc.perform(post("/api/telegram/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(SECRET_HEADER, "correct-secret")
                        .content(BODY))
                .andExpect(status().isOk());
        verify(updateHandler).handle(any());
    }
}
