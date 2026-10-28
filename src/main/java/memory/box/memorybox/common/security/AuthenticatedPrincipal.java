package memory.box.memorybox.common.security;

import memory.box.memorybox.user.IdentityProvider;

import java.util.UUID;

/**
 * The authenticated caller, carried as the principal of the Spring Security
 * {@code Authentication}. {@code userId} is the only trustworthy source of "who is
 * making this request" anywhere in the application — it must never be taken from a
 * request body or query parameter.
 */
public record AuthenticatedPrincipal(UUID userId, IdentityProvider provider) {
}
