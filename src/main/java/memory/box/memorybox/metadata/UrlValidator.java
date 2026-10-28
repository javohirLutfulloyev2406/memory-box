package memory.box.memorybox.metadata;

import memory.box.memorybox.common.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * Syntactic validation of a user-submitted URL. This only checks the URL is
 * well-formed and uses an allowed scheme — it does not touch the network, so it says
 * nothing about whether the destination is safe to fetch (see
 * {@code metadata.SsrfGuard} for that, applied only when the server actually fetches a
 * page for metadata extraction).
 */
@Component
public class UrlValidator {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_URL_LENGTH = 2048;

    public URI validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidUrlException("URL must not be blank.");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.length() > MAX_URL_LENGTH) {
            throw new InvalidUrlException("URL exceeds the maximum allowed length of " + MAX_URL_LENGTH + " characters.");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException ex) {
            throw new InvalidUrlException("URL is malformed: " + ex.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase())) {
            throw new InvalidUrlException("Only http and https URLs are supported.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new InvalidUrlException("URL must include a host.");
        }
        return uri;
    }
}
