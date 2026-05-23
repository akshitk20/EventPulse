package com.eventpulse.service;

import com.eventpulse.domain.user.User;
import com.eventpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

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
     */
    @Transactional
    public User upsertFromOauth(String provider, String subject, String email, String displayName) {
        return userRepository.findByOauthProviderAndOauthSubject(provider, subject)
            .map(existing -> {
                existing.setDisplayName(displayName);
                existing.setLastSeenAt(OffsetDateTime.now());
                return existing;
            })
            .orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .displayName(displayName)
                .oauthProvider(provider)
                .oauthSubject(subject)
                .lastSeenAt(OffsetDateTime.now())
                .build()));
    }

    @Transactional
    public void touchLastSeen(UUID userId) {
        userRepository.findById(userId)
            .ifPresent(u -> u.setLastSeenAt(OffsetDateTime.now()));
    }
}
