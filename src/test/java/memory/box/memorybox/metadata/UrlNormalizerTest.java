package memory.box.memorybox.metadata;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class UrlNormalizerTest {

    private final UrlNormalizer normalizer = new UrlNormalizer();

    @Test
    void lowercasesSchemeAndHost() {
        assertThat(normalizer.normalize(URI.create("HTTPS://Example.COM/Path")))
                .isEqualTo("https://example.com/Path");
    }

    @Test
    void stripsDefaultHttpsPort() {
        assertThat(normalizer.normalize(URI.create("https://example.com:443/path")))
                .isEqualTo("https://example.com/path");
    }

    @Test
    void stripsDefaultHttpPort() {
        assertThat(normalizer.normalize(URI.create("http://example.com:80/path")))
                .isEqualTo("http://example.com/path");
    }

    @Test
    void keepsNonDefaultPort() {
        assertThat(normalizer.normalize(URI.create("https://example.com:8443/path")))
                .isEqualTo("https://example.com:8443/path");
    }

    @Test
    void dropsFragment() {
        assertThat(normalizer.normalize(URI.create("https://example.com/path#section-2")))
                .isEqualTo("https://example.com/path");
    }

    @Test
    void preservesQueryStringExactly() {
        assertThat(normalizer.normalize(URI.create("https://example.com/watch?v=abc123&t=42")))
                .isEqualTo("https://example.com/watch?v=abc123&t=42");
    }
}
