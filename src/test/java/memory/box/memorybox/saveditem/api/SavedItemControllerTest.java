package memory.box.memorybox.saveditem.api;

import memory.box.memorybox.common.security.CurrentUserProvider;
import memory.box.memorybox.saveditem.application.SaveOutcome;
import memory.box.memorybox.saveditem.application.SavedItemMapper;
import memory.box.memorybox.saveditem.application.SavedItemService;
import memory.box.memorybox.saveditem.domain.SavedItem;
import memory.box.memorybox.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-layer tests: HTTP status mapping, validation, and the unauthenticated
 * (dev-auth disabled) path. Business rules are covered separately against a mocked
 * {@link SavedItemService} — this layer only checks that the controller wires
 * requests/responses correctly.
 */
@WebMvcTest(SavedItemController.class)
@AutoConfigureMockMvc(addFilters = false)
class SavedItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SavedItemService savedItemService;
    @MockitoBean
    private SavedItemMapper savedItemMapper;
    @MockitoBean
    private CurrentUserProvider currentUserProvider;

    private final UUID userId = UUID.randomUUID();

    @Test
    void createWithBlankUrlReturns400WithFieldError() throws Exception {
        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("url"));
    }

    @Test
    void createReturns201WhenANewItemIsSaved() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        SavedItem item = new SavedItem(new User("Test User"), "https://example.com/a", "https://example.com/a", null);
        when(savedItemService.createOrGetItem(eq(userId), any())).thenReturn(new SaveOutcome(item, true));

        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void createReturns200WhenItemAlreadyExisted() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(userId);
        SavedItem item = new SavedItem(new User("Test User"), "https://example.com/a", "https://example.com/a", null);
        when(savedItemService.createOrGetItem(eq(userId), any())).thenReturn(new SaveOutcome(item, false));

        mockMvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/a\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deleteReturns204() throws Exception {
        when(currentUserProvider.currentUserId()).thenReturn(userId);

        mockMvc.perform(delete("/api/v1/items/" + UUID.randomUUID()))
                .andExpect(status().isNoContent());
    }
}
