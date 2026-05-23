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
     * The user's subscribed Interest entities, materialized inside the transaction so
     * callers can read displayName / category / etc. without a LazyInitializationException.
     * Sorted by displayName for stable rendering order.
     */
    @Transactional(readOnly = true)
    public List<Interest> subscribedInterests(UUID userId) {
        return userInterestRepository.findByUser_Id(userId).stream()
            .map(UserInterest::getInterest)
            .peek(i -> { i.getDisplayName(); i.getSlug(); i.getCategory(); })
            .sorted(java.util.Comparator.comparing(Interest::getDisplayName))
            .toList();
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
        for (UUID descendantId : collectDescendantIds(root.getId())) {
            Interest descendant = interestRepository.getReferenceById(descendantId);
            upsertOne(user, descendant, weight);
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
     * Removes the row for the requested interest and every descendant in the taxonomy,
     * mirroring the expansion in {@link #subscribe}. Without the cascade, unsubscribing
     * "Football" would leave premier-league / la-liga still subscribed (added at
     * subscribe time) and EPL items would keep showing up in the feed.
     */
    @Transactional
    public void unsubscribe(UUID userId, UUID interestId) {
        userInterestRepository.deleteByUser_IdAndInterest_Id(userId, interestId);
        for (UUID descendantId : collectDescendantIds(interestId)) {
            userInterestRepository.deleteByUser_IdAndInterest_Id(userId, descendantId);
        }
    }

    /**
     * BFS-walk the interest tree rooted at the given id and return the ids of every
     * descendant (not including the root itself). The taxonomy is small enough that
     * a per-node findByParent_Id query per BFS step is fine.
     */
    private Set<UUID> collectDescendantIds(UUID rootId) {
        Set<UUID> visited = new HashSet<>();
        Set<UUID> result = new HashSet<>();
        visited.add(rootId);
        Deque<UUID> queue = new ArrayDeque<>();
        queue.add(rootId);
        while (!queue.isEmpty()) {
            UUID current = queue.removeFirst();
            for (Interest child : interestRepository.findByParent_Id(current)) {
                if (visited.add(child.getId())) {
                    result.add(child.getId());
                    queue.add(child.getId());
                }
            }
        }
        return result;
    }
}
