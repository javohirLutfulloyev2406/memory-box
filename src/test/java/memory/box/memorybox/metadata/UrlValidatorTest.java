package memory.box.memorybox.metadata;

import memory.box.memorybox.common.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {

    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/article",
            "http://example.com",
            "https://example.com:8443/path?query=1"
    })
    void acceptsWellFormedHttpAndHttpsUrls(String url) {
        URI uri = validator.validate(url);
        assertThat(uri.toString()).isEqualTo(url);
    }

    @Test
    void rejectsBlankUrl() {
        assertThatThrownBy(() -> validator.validate("  "))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> validator.validate("ftp://example.com/file"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("http and https");
    }

    @Test
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> validator.validate("https://exa mple.com"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsUrlWithoutHost() {
        assertThatThrownBy(() -> validator.validate("https:///path-only"))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void rejectsExcessivelyLongUrl() {
        String longUrl = "https://example.com/" + "a".repeat(2100);
        assertThatThrownBy(() -> validator.validate(longUrl))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("maximum allowed length");
    }
}
