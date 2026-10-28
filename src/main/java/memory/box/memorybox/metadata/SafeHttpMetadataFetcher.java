package memory.box.memorybox.metadata;

import memory.box.memorybox.common.exception.InvalidUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal, safety-first title/description extraction. This is a first pass, not a
 * general content-extraction pipeline: it reads a bounded prefix of the HTML and pulls
 * out {@code <title>} and {@code <meta name="description">} with regex rather than a
 * full HTML parser. It will miss titles rendered only by client-side JavaScript (most
 * SPA-style pages) and unusual markup. TODO: replace with a proper HTML parser and/or
 * platform-specific extraction (oEmbed for YouTube, Open Graph tags, etc.) in a later
 * iteration — tracked as a known limitation, not implemented here.
 */
@Component
public class SafeHttpMetadataFetcher implements MetadataFetcher {

    private static final Logger log = LoggerFactory.getLogger(SafeHttpMetadataFetcher.class);
    private static final int MAX_REDIRECTS = 3;
    private static final Set<Integer> REDIRECT_STATUSES = Set.of(301, 302, 303, 307, 308);
    private static final Pattern TITLE_PATTERN = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern META_TAG_PATTERN = Pattern.compile("<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAME_DESCRIPTION_PATTERN = Pattern.compile("name\\s*=\\s*[\"']description[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT_ATTR_PATTERN = Pattern.compile("content\\s*=\\s*[\"']([^\"']*)[\"']", Pattern.CASE_INSENSITIVE);

    private final UrlValidator urlValidator;
    private final SsrfGuard ssrfGuard;
    private final MetadataFetchProperties properties;
    private final HttpClient httpClient;

    public SafeHttpMetadataFetcher(UrlValidator urlValidator, SsrfGuard ssrfGuard, MetadataFetchProperties properties) {
        this.urlValidator = urlValidator;
        this.ssrfGuard = ssrfGuard;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public Optional<FetchedMetadata> fetch(String url) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }
        try {
            return fetchFollowingRedirects(urlValidator.validate(url), 0);
        } catch (InvalidUrlException ex) {
            log.info("Metadata fetch refused for safety reasons: {}", ex.getMessage());
            return Optional.empty();
        } catch (IOException | InterruptedException ex) {
            log.info("Metadata fetch failed for a saved URL: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<FetchedMetadata> fetchFollowingRedirects(URI uri, int redirectCount) throws IOException, InterruptedException {
        if (redirectCount > MAX_REDIRECTS) {
            log.info("Metadata fetch aborted: too many redirects");
            return Optional.empty();
        }
        ssrfGuard.assertSafe(uri.getHost());

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .header("User-Agent", "MemoryBoxBot/1.0 (+metadata fetch)")
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();

        if (REDIRECT_STATUSES.contains(status)) {
            Optional<String> location = response.headers().firstValue("Location");
            response.body().close();
            if (location.isEmpty()) {
                return Optional.empty();
            }
            URI next = urlValidator.validate(uri.resolve(location.get()).toString());
            return fetchFollowingRedirects(next, redirectCount + 1);
        }

        if (status < 200 || status >= 300) {
            response.body().close();
            return Optional.empty();
        }

        String html = readBounded(response.body(), properties.getMaxResponseBytes());
        return Optional.of(new FetchedMetadata(extractTitle(html), extractDescription(html)));
    }

    private String readBounded(InputStream in, long maxBytes) throws IOException {
        try (InputStream stream = in) {
            byte[] buffer = stream.readNBytes((int) Math.min(maxBytes, Integer.MAX_VALUE));
            return new String(buffer, StandardCharsets.UTF_8);
        }
    }

    private String extractTitle(String html) {
        Matcher matcher = TITLE_PATTERN.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        return cleanText(matcher.group(1), 500);
    }

    private String extractDescription(String html) {
        Matcher tagMatcher = META_TAG_PATTERN.matcher(html);
        while (tagMatcher.find()) {
            String tag = tagMatcher.group();
            if (NAME_DESCRIPTION_PATTERN.matcher(tag).find()) {
                Matcher contentMatcher = CONTENT_ATTR_PATTERN.matcher(tag);
                if (contentMatcher.find()) {
                    return cleanText(contentMatcher.group(1), 1000);
                }
            }
        }
        return null;
    }

    private String cleanText(String raw, int maxLength) {
        if (raw == null) {
            return null;
        }
        String unescaped = raw
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'");
        String collapsed = unescaped.replaceAll("\\s+", " ").trim();
        if (collapsed.isEmpty()) {
            return null;
        }
        return collapsed.length() > maxLength ? collapsed.substring(0, maxLength) : collapsed;
    }
}
