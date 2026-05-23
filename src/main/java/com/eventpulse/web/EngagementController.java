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
import org.springframework.web.bind.annotation.RequestHeader;
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
                         @RequestHeader(name = "HX-Detail", required = false) String detailHeader,
                         Authentication authentication,
                         Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        engagementService.record(user.getId(), feedItemId, action);

        model.addAttribute("feedItemId", feedItemId);
        model.addAttribute("action", action);
        // Detail page sends HX-Detail:1 — return the static ack so the swap stays
        // inside the action bar. List pages (no header) get the inline Undo panel.
        if (action == EngagementAction.HIDE && detailHeader == null) {
            return "fragments/engagement-ack :: hide-undo";
        }
        return "fragments/engagement-ack :: ack";
    }

    /**
     * Undo a previously-recorded engagement. The HTMX swap restores the active
     * action bar so the user can re-engage. Idempotent — missing row is a no-op.
     */
    @PostMapping("/{action}/{feedItemId}/undo")
    public String undo(@PathVariable EngagementAction action,
                       @PathVariable UUID feedItemId,
                       Authentication authentication,
                       Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        engagementService.delete(user.getId(), feedItemId, action);

        model.addAttribute("feedItemId", feedItemId);
        return "fragments/engagement-ack :: actions";
    }
}
