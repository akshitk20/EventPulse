package com.eventpulse.service;

import com.eventpulse.domain.interest.UserInterest;
import com.eventpulse.domain.user.User;
import com.eventpulse.repository.InterestRepository;
import com.eventpulse.repository.UserInterestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cold-start UX guarantee: a brand-new OAuth signup must produce user_interest rows
 * for every root interest (and their descendants via the BFS cascade in
 * {@link UserInterestService#subscribe}). Without this, /feed renders empty for the
 * entire window between signup and the user manually opening /interests — which is
 * the #1 churn vector for a feed product.
 *
 * <p>Re-login for the same OAuth subject must NOT re-run the subscribe. Repeated logins
 * happen on every session refresh; a re-subscribe would silently reset weights the user
 * had pruned via the /interests UI.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserServiceAutoSubscribeTest {

    @Autowired UserService userService;
    @Autowired UserInterestRepository userInterestRepository;
    @Autowired InterestRepository interestRepository;

    @Test
    @Transactional
    void newUserGetsSubscribedToAllRootInterestsAndDescendants() {
        String subject = "auto-sub-" + UUID.randomUUID();
        String email = "auto-sub-" + UUID.randomUUID() + "@test.local";

        User created = userService.upsertFromOauth("google", subject, email, "New User");

        List<UserInterest> subs = userInterestRepository.findByUser_Id(created.getId());
        assertThat(subs)
            .as("auto-subscribe should create at least one row per root interest")
            .isNotEmpty();

        // Every root interest must be present.
        long rootCount = interestRepository.findByParentIsNull().size();
        long subscribedRootCount = subs.stream()
            .map(UserInterest::getInterest)
            .filter(i -> i.getParent() == null)
            .count();
        assertThat(subscribedRootCount)
            .as("every root interest should be subscribed")
            .isEqualTo(rootCount);

        // Cascade: descendants get subscribed too (V2 seeds a multi-level taxonomy).
        long totalInterests = interestRepository.count();
        assertThat((long) subs.size())
            .as("descendants should also be subscribed via the BFS cascade")
            .isEqualTo(totalInterests);
    }

    @Test
    @Transactional
    void existingUserReloginDoesNotResetSubscriptions() {
        String subject = "relogin-" + UUID.randomUUID();
        String email = "relogin-" + UUID.randomUUID() + "@test.local";

        User created = userService.upsertFromOauth("google", subject, email, "First Login");
        int initialCount = userInterestRepository.findByUser_Id(created.getId()).size();
        assertThat(initialCount).as("first login auto-subscribes").isPositive();

        // Simulate the user pruning a subscription, then logging in again.
        UserInterest first = userInterestRepository.findByUser_Id(created.getId()).getFirst();
        UUID prunedInterestId = first.getInterest().getId();
        userInterestRepository.delete(first);

        User refound = userService.upsertFromOauth("google", subject, email, "Second Login");
        assertThat(refound.getId()).isEqualTo(created.getId());

        List<UserInterest> after = userInterestRepository.findByUser_Id(refound.getId());
        assertThat(after)
            .as("re-login must not re-add the pruned interest")
            .extracting(ui -> ui.getInterest().getId())
            .doesNotContain(prunedInterestId);
        assertThat(after.size())
            .as("count should be initialCount - 1 after pruning, not back to initialCount")
            .isEqualTo(initialCount - 1);
    }
}
