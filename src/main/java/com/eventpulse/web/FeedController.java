package com.eventpulse.web;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.user.User;
import com.eventpulse.security.CurrentUserResolver;
import com.eventpulse.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class FeedController {

    private static final int PAGE_SIZE = 20;

    private final FeedService feedService;
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
}
