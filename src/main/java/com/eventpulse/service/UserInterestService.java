package com.eventpulse.service;

import com.eventpulse.domain.interest.Interest;
import com.eventpulse.domain.interest.UserInterest;
import com.eventpulse.domain.interest.UserInterestId;
import com.eventpulse.domain.user.User;
import com.eventpulse.repository.InterestRepository;
import com.eventpulse.repository.UserInterestRepository;
import com.eventpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserInterestService {

    private final UserInterestRepository userInterestRepository;
    private final UserRepository userRepository;
    private final InterestRepository interestRepository;

    @Transactional(readOnly = true)
    public List<UserInterest> forUser(UUID userId) {
        return userInterestRepository.findByUser_Id(userId);
    }

    /**
     * Subscribes the user to the given interest AND every descendant in the taxonomy.
     *
     * Why expand downward: the feed query does an exact interest-id match, so subscribing
     * only to "Sports" would miss items tagged with "Cricket" or "F1". The taxonomy is
     * small (~35 rows) and immutable per release, so we materialize descendants at
     * subscribe time rather than expanding at query time. Idempotent — re-subscribing
     * just refreshes the weight on existing rows.
     *
     * Direction matters: we expand to descendants only, never ancestors. Subscribing to
     * "Cricket" should NOT auto-subscribe to "Sports" — that would surface unrelated
     * football items in the user's feed.
     */
    @Transactional
    public UserInterest subscribe(UUID userId, UUID interestId, float weight) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Interest root = interestRepository.findById(interestId)
            .orElseThrow(() -> new IllegalArgumentException("interest not found: " + interestId));

        UserInterest savedRoot = upsertOne(user, root, weight);

        Set<UUID> visited = new HashSet<>();
        visited.add(root.getId());
        Deque<Interest> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            Interest current = queue.removeFirst();
            for (Interest child : interestRepository.findByParent_Id(current.getId())) {
                if (visited.add(child.getId())) {
                    upsertOne(user, child, weight);
                    queue.add(child);
                }
            }
        }
        return savedRoot;
    }

    private UserInterest upsertOne(User user, Interest interest, float weight) {
        UserInterestId pk = new UserInterestId(user.getId(), interest.getId());
        return userInterestRepository.findById(pk)
            .map(existing -> { existing.setWeight(weight); return existing; })
            .orElseGet(() -> userInterestRepository.save(UserInterest.builder()
                .id(pk)
                .user(user)
                .interest(interest)
                .weight(weight)
                .build()));
    }

    /**
     * Removes only the exact (user, interest) row. We deliberately do NOT cascade to
     * descendants — the user may have subscribed to children individually and would
     * lose those if we auto-removed them along with a parent unsubscribe.
     */
    @Transactional
    public void unsubscribe(UUID userId, UUID interestId) {
        userInterestRepository.deleteByUser_IdAndInterest_Id(userId, interestId);
    }
}
