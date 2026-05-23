package com.eventpulse.web;

import com.eventpulse.domain.engagement.EngagementAction;
import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.user.User;
import com.eventpulse.security.CurrentUserResolver;
import com.eventpulse.service.EngagementService;
import com.eventpulse.service.FeedService;
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

    private final FeedService feedService;
    private final EngagementService engagementService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/feed")
    public String feed(Authentication authentication,
                       @RequestParam(defaultValue = "0") int page,
                       Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        List<FeedItem> items = feedService.personalizedFeed(
            user.getId(),
            PageRequest.of(Math.max(page, 0), PAGE_SIZE)
        );

        model.addAttribute("user", user);
        model.addAttribute("items", items);
        model.addAttribute("page", page);
        model.addAttribute("hasNext", items.size() == PAGE_SIZE);
        return "feed";
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
