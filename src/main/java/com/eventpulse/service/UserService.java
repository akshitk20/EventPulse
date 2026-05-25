package com.eventpulse.service;

import com.eventpulse.domain.interest.Interest;
import com.eventpulse.domain.user.User;
import com.eventpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    /**
     * Default weight assigned to root-interest subscriptions auto-created at signup.
     * Matches the weight a manually-clicked "Subscribe" produces in {@link UserInterestService},
     * so users who later toggle one off have the same row to delete as they would for any
     * other subscription.
     */
    private static final float DEFAULT_SIGNUP_WEIGHT = 1.0f;

    private final UserRepository userRepository;
    private final UserInterestService userInterestService;
    private final InterestService interestService;

    @Transactional(readOnly = true)
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByOauth(String provider, String subject) {
        return userRepository.findByOauthProviderAndOauthSubject(provider, subject);
    }

    /**
     * Find or create a user from OAuth login. If found, refresh display name + last seen.
     *
     * <p>Brand-new users are auto-subscribed to every root interest (cascading to descendants
     * via {@link UserInterestService#subscribe}). Why: without this, a first-time visitor lands
     * on /feed with zero matching items because the feed query joins on user_interests. The
     * empty state is the #1 source of cold-start churn — auto-subscribing means the feed is
     * populated the moment the next polling cycle runs, and users can prune from /interests
     * if they don't want a category. Existing users are untouched.
     */
    @Transactional
    public User upsertFromOauth(String provider, String subject, String email, String displayName) {
        Optional<User> existing = userRepository.findByOauthProviderAndOauthSubject(provider, subject);
        if (existing.isPresent()) {
            User u = existing.get();
            u.setDisplayName(displayName);
            u.setLastSeenAt(OffsetDateTime.now());
            return u;
        }
        User created = userRepository.save(User.builder()
            .email(email)
            .displayName(displayName)
            .oauthProvider(provider)
            .oauthSubject(subject)
            .lastSeenAt(OffsetDateTime.now())
            .build());
        autoSubscribeRoots(created.getId());
        return created;
    }

    private void autoSubscribeRoots(UUID userId) {
        List<Interest> roots = interestService.rootInterests();
        for (Interest root : roots) {
            userInterestService.subscribe(userId, root.getId(), DEFAULT_SIGNUP_WEIGHT);
        }
        log.info("auto-subscribed new user {} to {} root interests", userId, roots.size());
    }

    @Transactional
    public void touchLastSeen(UUID userId) {
        userRepository.findById(userId)
            .ifPresent(u -> u.setLastSeenAt(OffsetDateTime.now()));
    }
}
