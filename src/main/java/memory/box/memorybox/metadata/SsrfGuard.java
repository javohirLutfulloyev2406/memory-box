package memory.box.memorybox.metadata;

import memory.box.memorybox.common.exception.InvalidUrlException;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Refuses to let the metadata fetcher connect to anything that isn't a public internet
 * address: loopback, RFC1918/ULA private ranges, link-local (which also covers the
 * 169.254.169.254 cloud metadata endpoint), multicast, and the "any local" wildcard.
 *
 * <p>Known limitation: this validates the address(es) a hostname resolves to <em>at
 * check time</em>. A malicious DNS server could still return a safe address here and a
 * private one on a later lookup (DNS rebinding) if the underlying HTTP client
 * re-resolves mid-request. {@code SafeHttpMetadataFetcher} mitigates this by resolving
 * once per hop and re-validating on every redirect, but does not pin the exact
 * validated socket address for the connection itself — a fuller fix would plug a
 * custom resolver into the HTTP client. Acceptable for this MVP's threat model
 * (fetching links the item's own owner chose to save), called out here for later
 * hardening.
 */
@Component
public class SsrfGuard {

    public void assertSafe(String host) {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException ex) {
            throw new InvalidUrlException("Could not resolve host: " + host);
        }
        for (InetAddress address : addresses) {
            if (isDisallowed(address)) {
                throw new InvalidUrlException("Refusing to fetch an internal or non-public network address.");
            }
        }
    }

    private boolean isDisallowed(InetAddress address) {
        if (address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || address.isAnyLocalAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // IPv6 unique local addresses (fc00::/7) are not covered by isSiteLocalAddress().
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
