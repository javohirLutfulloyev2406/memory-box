package memory.box.memorybox.metadata;

import memory.box.memorybox.common.exception.InvalidUrlException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Uses literal IP addresses (never real DNS lookups, so this stays deterministic and
 * network-independent) to verify every disallowed address class is actually blocked.
 */
class SsrfGuardTest {

    private final SsrfGuard guard = new SsrfGuard();

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",       // IPv4 loopback
            "::1",             // IPv6 loopback
            "169.254.169.254", // link-local, incl. the cloud metadata endpoint
            "10.0.0.1",        // RFC1918 private
            "192.168.1.1",     // RFC1918 private
            "172.16.0.1",      // RFC1918 private
            "224.0.0.1",       // multicast
            "0.0.0.0",         // any-local / wildcard
            "fc00::1",         // IPv6 unique local address (ULA)
    })
    void blocksNonPublicAddresses(String address) {
        assertThatThrownBy(() -> guard.assertSafe(address))
                .isInstanceOf(InvalidUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"8.8.8.8", "1.1.1.1"})
    void allowsPublicAddresses(String address) {
        assertThatCode(() -> guard.assertSafe(address)).doesNotThrowAnyException();
    }

    @Test
    void rejectsUnresolvableHost() {
        assertThatThrownBy(() -> guard.assertSafe("this-host-does-not-exist.invalid"))
                .isInstanceOf(InvalidUrlException.class)
                .hasMessageContaining("resolve");
    }
}
