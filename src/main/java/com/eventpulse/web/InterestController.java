package com.eventpulse.web;

import com.eventpulse.domain.interest.Interest;
import com.eventpulse.domain.interest.InterestCategory;
import com.eventpulse.domain.interest.UserInterest;
import com.eventpulse.domain.user.User;
import com.eventpulse.repository.InterestRepository;
import com.eventpulse.security.CurrentUserResolver;
import com.eventpulse.service.UserInterestService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * GET  /interests                       -> page with the full taxonomy and current subscriptions
 * POST /interests/{id}/subscribe        -> add a UserInterest (idempotent), returns toggle fragment
 * POST /interests/{id}/unsubscribe      -> remove the UserInterest, returns toggle fragment
 *
 * Two POSTs (instead of POST + DELETE) keep the HTMX templates simple — both calls return the
 * same fragment so swap targets stay symmetric.
 */
@Controller
@RequestMapping("/interests")
@RequiredArgsConstructor
public class InterestController {

    private final InterestRepository interestRepository;
    private final UserInterestService userInterestService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public String index(Authentication authentication, Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        List<Interest> all = interestRepository.findAll();

        Set<UUID> subscribedIds = userInterestService.forUser(user.getId()).stream()
            .map(ui -> ui.getInterest().getId())
            .collect(Collectors.toSet());

        Map<UUID, List<Interest>> childrenByParent = all.stream()
            .filter(i -> i.getParent() != null)
            .collect(Collectors.groupingBy(i -> i.getParent().getId()));

        Map<InterestCategory, List<Interest>> rootsByCategory = all.stream()
            .filter(i -> i.getParent() == null)
            .collect(Collectors.groupingBy(
                Interest::getCategory,
                LinkedHashMap::new,
                Collectors.toList()
            ));

        model.addAttribute("rootsByCategory", rootsByCategory);
        model.addAttribute("childrenByParent", childrenByParent);
        model.addAttribute("subscribedIds", subscribedIds);
        return "interests";
    }

    @PostMapping("/{interestId}/subscribe")
    public String subscribe(@PathVariable UUID interestId,
                            Authentication authentication,
                            Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        UserInterest ui = userInterestService.subscribe(user.getId(), interestId, 1.0f);

        model.addAttribute("interestId", interestId);
        model.addAttribute("subscribed", true);
        model.addAttribute("displayName", ui.getInterest().getDisplayName());
        return "fragments/interest-toggle :: toggle";
    }

    @PostMapping("/{interestId}/unsubscribe")
    public String unsubscribe(@PathVariable UUID interestId,
                              Authentication authentication,
                              Model model) {
        User user = currentUserResolver.resolve(authentication)
            .orElseThrow(() -> new IllegalStateException("authenticated principal has no local user row"));

        userInterestService.unsubscribe(user.getId(), interestId);

        Interest interest = interestRepository.findById(interestId)
            .orElseThrow(() -> new IllegalArgumentException("interest not found: " + interestId));

        model.addAttribute("interestId", interestId);
        model.addAttribute("subscribed", false);
        model.addAttribute("displayName", interest.getDisplayName());
        return "fragments/interest-toggle :: toggle";
    }
}
