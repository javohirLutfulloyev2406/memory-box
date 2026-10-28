package memory.box.memorybox.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the development-only authentication bypass. See
 * {@link DevAuthenticationFilter} for what this actually does, and README.md
 * "Authentication" for why it must never be enabled in a production deployment.
 */
@ConfigurationProperties(prefix = "app.security.dev-auth")
public class DevAuthProperties {

    private boolean enabled = false;
    private String displayName = "Dev User";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
