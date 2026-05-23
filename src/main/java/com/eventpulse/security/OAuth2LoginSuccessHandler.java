package com.eventpulse.security;

import com.eventpulse.domain.user.User;
import com.eventpulse.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Persists/updates the local User row for the authenticated OAuth2 principal,
 * then forwards to the post-login landing page.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        setDefaultTargetUrl("/feed");
        if (authentication.getPrincipal() instanceof OAuth2User principal) {
            String subject = principal.getAttribute("sub");
            String email = principal.getAttribute("email");
            String name = principal.getAttribute("name");
            if (subject != null && email != null) {
                User user = userService.upsertFromOauth("google", subject, email, name);
                log.debug("oauth2 login: user={} email={}", user.getId(), user.getEmail());
            } else {
                log.warn("oauth2 principal missing sub/email: attrs={}", principal.getAttributes().keySet());
            }
        }
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
