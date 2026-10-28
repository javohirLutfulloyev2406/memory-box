package memory.box.memorybox.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import memory.box.memorybox.auth.IdentityResolutionService;
import memory.box.memorybox.user.IdentityProvider;
import memory.box.memorybox.user.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Development-only authentication: when {@code app.security.dev-auth.enabled=true},
 * every request is authenticated as one fixed user so the API can be exercised before
 * a real login flow exists.
 *
 * <p><strong>This is not an authentication mechanism suitable for any environment
 * reachable by more than one person.</strong> It performs no credential check at all —
 * it simply trusts that nothing untrusted can reach this process. Before a public
 * deployment this filter must be disabled (the default) and replaced with real
 * authentication (e.g. OAuth2/OIDC login for the web frontend; the Telegram webhook
 * already identifies users via their Telegram account and does not use this filter).
 */
public class DevAuthenticationFilter extends OncePerRequestFilter {

    private static final String DEV_PROVIDER_USER_ID = "dev-user";

    private final DevAuthProperties properties;
    private final IdentityResolutionService identityResolutionService;

    public DevAuthenticationFilter(DevAuthProperties properties, IdentityResolutionService identityResolutionService) {
        this.properties = properties;
        this.identityResolutionService = identityResolutionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (properties.isEnabled() && SecurityContextHolder.getContext().getAuthentication() == null) {
            User devUser = identityResolutionService.resolveOrCreateUser(
                    IdentityProvider.DEV, DEV_PROVIDER_USER_ID, properties.getDisplayName());
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(devUser.getId(), IdentityProvider.DEV);
            var authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
