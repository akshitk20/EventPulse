package com.eventpulse.security;

import com.eventpulse.domain.user.User;
import com.eventpulse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Resolves the local User row for the currently authenticated OAuth2 principal.
 * The row is created on first login by {@link OAuth2LoginSuccessHandler}, so this
 * is purely a lookup — never an upsert.
 */
@Component
@RequiredArgsConstructor
public class CurrentUserResolver {

    private final UserService userService;

    public Optional<User> resolve(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof OAuth2User principal)) {
            return Optional.empty();
        }
        String subject = principal.getAttribute("sub");
        if (subject == null) {
            return Optional.empty();
        }
        return userService.findByOauth("google", subject);
    }
}
