package memory.box.memorybox.common.security;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Single place the application reads "who is calling right now" from. Every saved-item
 * and search operation must go through this instead of trusting a client-supplied id.
 */
@Component
public class CurrentUserProvider {

    public UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new InsufficientAuthenticationException("No authenticated Memory Box user for this request.");
        }
        return principal.userId();
    }
}
