package com.eventpulse.web;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.user.User;
import com.eventpulse.service.FeedService;
import com.eventpulse.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Public, no-login preview of the personalized feed. Renders the same item
 * cards as {@code /feed} so HN/Reddit/Twitter visitors can see real, ranked
 * items before committing to OAuth — eliminating the cold-start friction
 * that makes "Sign in with Google" the wall on the landing page.
 *
 * <p>Backed by a sentinel "demo" user that's auto-provisioned on first hit.
 * The OAuth-subject pair {@code ("demo", DEMO_OAUTH_SUBJECT)} can't collide
 * with any real Google {@code sub} (Google subs are numeric, never the
 * literal string we use here). The demo user goes through the same
 * {@link UserService#upsertFromOauth} path as a real signup, so it
 * inherits the auto-subscribe-to-roots behavior — meaning the personalized
 * feed query has something to join on and returns ranked items.
 *
 * <p>The rendered template ({@code demo.html}) intentionally omits the
 * Save / Hide / Open-detail actions: no anonymous visitor should be able
 * to mutate engagement state for the demo user, and the read-only view
 * matches the conversion intent — show the product, then send to /login.
 */
@Controller
@RequiredArgsConstructor
public class DemoController {

    /**
     * OAuth subject for the singleton demo account. {@code oauth_provider} +
     * {@code oauth_subject} have a UNIQUE constraint, so subsequent calls to
     * {@code upsertFromOauth} are no-ops on this row — they just refresh
     * {@code last_seen_at}, which is harmless.
     */
    private static final String DEMO_OAUTH_PROVIDER = "demo";
    private static final String DEMO_OAUTH_SUBJECT = "demo-user-singleton";
    private static final String DEMO_EMAIL = "demo@eventpulse.local";
    private static final String DEMO_DISPLAY_NAME = "Demo";

    private static final int PAGE_SIZE = 20;

    private final UserService userService;
    private final FeedService feedService;

    @GetMapping("/demo")
    public String demo(@RequestParam(defaultValue = "0") int page, Model model) {
        User demoUser = userService.upsertFromOauth(
            DEMO_OAUTH_PROVIDER, DEMO_OAUTH_SUBJECT, DEMO_EMAIL, DEMO_DISPLAY_NAME);

        List<FeedItem> items = feedService.personalizedFeed(
            demoUser.getId(),
            PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        model.addAttribute("items", items);
        model.addAttribute("page", page);
        model.addAttribute("hasNext", items.size() == PAGE_SIZE);
        return "demo";
    }
}
