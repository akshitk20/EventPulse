package com.eventpulse.web;

import com.eventpulse.domain.engagement.EngagementAction;
import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.interest.Interest;
import com.eventpulse.domain.user.User;
import com.eventpulse.security.CurrentUserResolver;
import com.eventpulse.service.EngagementService;
import com.eventpulse.service.FeedService;
import com.eventpulse.service.UserInterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class FeedController {

    private static final int PAGE_SIZE = 20;
    private static final List<String> SOURCES = List.of("hn", "reddit", "sportsdb");

    private final FeedService feedService;
    private final EngagementService engagementService;
    private final UserInterestService userInterestService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/feed")
    public String feed(Authentication authentication,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String source,
                       @RequestParam(required = false) UUID interest,
                       Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        boolean filtered = (q != null && !q.isBlank())
                        || (source != null && !source.isBlank())
                        || interest != null;

        List<FeedItem> items = filtered
            ? feedService.searchPersonalizedFeed(
                user.getId(), q, source, interest,
                PageRequest.of(Math.max(page, 0), PAGE_SIZE))
            : feedService.personalizedFeed(
                user.getId(),
                PageRequest.of(Math.max(page, 0), PAGE_SIZE));

        // Subscribed interests for chip rendering. The service materializes
        // each Interest's fields inside its transaction so display rendering
        // doesn't trip a LazyInitializationException.
        List<Interest> subscribed = userInterestService.subscribedInterests(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("items", items);
        model.addAttribute("page", page);
        model.addAttribute("hasNext", items.size() == PAGE_SIZE);
        model.addAttribute("q", q);
        model.addAttribute("activeSource", source);
        model.addAttribute("activeInterestId", interest);
        model.addAttribute("subscribedInterests", subscribed);
        model.addAttribute("sources", SOURCES);
        return "feed";
    }

    /**
     * Saved view — items the user has SAVE-engaged, newest-saved first.
     * Uses the same card layout as /feed but pulls from the saved query.
     */
    @GetMapping("/feed/saved")
    public String saved(Authentication authentication,
                        @RequestParam(defaultValue = "0") int page,
                        Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        List<FeedItem> items = feedService.savedFeed(
            user.getId(),
            PageRequest.of(Math.max(page, 0), PAGE_SIZE)
        );

        model.addAttribute("user", user);
        model.addAttribute("items", items);
        model.addAttribute("page", page);
        model.addAttribute("hasNext", items.size() == PAGE_SIZE);
        return "feed-saved";
    }

    /**
     * Hidden view — items the user has HIDE-engaged. Lets users review what
     * they've hidden and undo. Without this page, Hide is a black hole.
     */
    @GetMapping("/feed/hidden")
    public String hidden(Authentication authentication,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        List<FeedItem> items = feedService.hiddenFeed(
            user.getId(),
            PageRequest.of(Math.max(page, 0), PAGE_SIZE)
        );

        model.addAttribute("user", user);
        model.addAttribute("items", items);
        model.addAttribute("page", page);
        model.addAttribute("hasNext", items.size() == PAGE_SIZE);
        return "feed-hidden";
    }

    /**
     * Per-item detail view. Records a CLICK engagement on open — composite PK on
     * UserEngagement keeps re-visits idempotent. Branches on item.source for
     * source-specific metadata in the template.
     */
    @GetMapping("/feed/{id}")
    public String detail(@PathVariable UUID id,
                         Authentication authentication,
                         Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        FeedItem item = feedService.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "feed item not found"));

        engagementService.record(user.getId(), id, EngagementAction.CLICK);

        model.addAttribute("user", user);
        model.addAttribute("item", item);
        return "feed-detail";
    }
}
