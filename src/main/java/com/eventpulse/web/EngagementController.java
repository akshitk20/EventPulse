package com.eventpulse.web;

import com.eventpulse.domain.engagement.EngagementAction;
import com.eventpulse.domain.user.User;
import com.eventpulse.security.CurrentUserResolver;
import com.eventpulse.service.EngagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.UUID;

/**
 * HTMX endpoints for engagement actions. Each POST records the engagement and
 * returns a small fragment that swaps the action bar in place. The composite PK
 * makes record() idempotent.
 */
@Controller
@RequestMapping("/engagements")
@RequiredArgsConstructor
public class EngagementController {

    private final EngagementService engagementService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping("/{action}/{feedItemId}")
    public String record(@PathVariable EngagementAction action,
                         @PathVariable UUID feedItemId,
                         Authentication authentication,
                         Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        engagementService.record(user.getId(), feedItemId, action);

        model.addAttribute("feedItemId", feedItemId);
        model.addAttribute("action", action);
        return "fragments/engagement-ack :: ack";
    }
}
