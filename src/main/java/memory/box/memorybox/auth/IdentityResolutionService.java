package memory.box.memorybox.auth;

import memory.box.memorybox.user.IdentityProvider;
import memory.box.memorybox.user.User;
import memory.box.memorybox.user.UserIdentity;
import memory.box.memorybox.user.UserIdentityRepository;
import memory.box.memorybox.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds the Memory Box {@link User} behind an external identity (a Telegram account,
 * the fixed dev-auth identity, etc.), creating both the user and the identity link on
 * first contact.
 *
 * <p>Known limitation: if the exact same brand-new external identity sends two
 * concurrent first-time requests, both could attempt to insert a row and one would
 * fail on the (provider, provider_user_id) unique constraint. This is extremely
 * unlikely in practice (it requires a true race on someone's very first message) and
 * is left as a follow-up rather than adding cross-transaction retry machinery now.
 */
@Service
public class IdentityResolutionService {

    private final UserRepository userRepository;
    private final UserIdentityRepository userIdentityRepository;

    public IdentityResolutionService(UserRepository userRepository, UserIdentityRepository userIdentityRepository) {
        this.userRepository = userRepository;
        this.userIdentityRepository = userIdentityRepository;
    }

    @Transactional
    public User resolveOrCreateUser(IdentityProvider provider, String providerUserId, String displayNameIfNew) {
        return userIdentityRepository.findByProviderAndProviderUserId(provider, providerUserId)
                .map(UserIdentity::getUser)
                .orElseGet(() -> createUserWithIdentity(provider, providerUserId, displayNameIfNew));
    }

    private User createUserWithIdentity(IdentityProvider provider, String providerUserId, String displayName) {
        User user = userRepository.save(new User(displayName));
        userIdentityRepository.save(new UserIdentity(user, provider, providerUserId));
        return user;
    }
}
