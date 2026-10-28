package memory.box.memorybox.metadata;

import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * Conservative URL normalization used to detect per-user duplicates. Deliberately
 * does NOT strip or reorder query parameters: many sites encode the actual resource in
 * the query string (video ids, document ids, tracking that still determines content),
 * so removing any of it could silently merge two different destinations. It only
 * normalizes parts that are safe by construction:
 * <ul>
 *   <li>lower-cases the scheme and host (both are case-insensitive by spec)</li>
 *   <li>drops the default port for the scheme (http:80, https:443)</li>
 *   <li>drops the fragment (#...) — purely client-side, never sent to the server</li>
 * </ul>
 * The path, query string, and any non-default port are preserved exactly as given.
 */
@Component
public class UrlNormalizer {

    public String normalize(URI uri) {
        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();
        boolean isDefaultPort = (port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme));

        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (port != -1 && !isDefaultPort) {
            sb.append(':').append(port);
        }
        String path = uri.getRawPath();
        if (path != null) {
            sb.append(path);
        }
        if (uri.getRawQuery() != null) {
            sb.append('?').append(uri.getRawQuery());
        }
        return sb.toString();
    }
}
