package com.eventpulse.repository;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.interest.Interest;
import com.eventpulse.domain.interest.UserInterest;
import com.eventpulse.domain.interest.UserInterestId;
import com.eventpulse.domain.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cold-start: a brand new user with no engagement history must get a sane,
 * recency-ordered feed -- not an error, not an empty result when their
 * interests do match items.
 *
 * The three weight axes (source, per-interest, trending) all go to 0 via
 * {@code coalesce(..., 0.0)} when the CTEs return no rows for this user;
 * ordering then collapses to {@code published_at DESC, fetched_at DESC}.
 * This test guards that behaviour against future query changes.
 *
 * Runs against the local eventpulse_test Postgres database (created out of
 * band -- see README). Testcontainers would be cleaner but Docker Desktop's
 * /info endpoint is currently misbehaving; revisit once that's resolved.
 */
@SpringBootTest
@ActiveProfiles("test")
class FeedItemRepositoryColdStartTest {

    @Autowired FeedItemRepository feedItemRepository;
    @Autowired UserRepository userRepository;
    @Autowired InterestRepository interestRepository;

    @PersistenceContext EntityManager em;

    /**
     * The test runs inside a single transaction that we let Spring roll back,
     * so all inserts are reverted. We don't need a manual cleanup hook; the
     * @Transactional on the test method provides it.
     */
    @Test
    @Transactional
    void coldStartReturnsRecencyOrderedFeedWithoutErrors() {
        // V2 seeds the interests taxonomy, so at least one interest exists.
        Interest someInterest = interestRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "no interests seeded -- check V2 migration"));

        User fresh = userRepository.save(User.builder()
                .email("cold-start-" + UUID.randomUUID() + "@test.local")
                .oauthProvider("google")
                .oauthSubject("cold-" + UUID.randomUUID())
                .build());

        em.persist(UserInterest.builder()
                .id(new UserInterestId(fresh.getId(), someInterest.getId()))
                .user(fresh)
                .interest(someInterest)
                .weight(1.0f)
                .build());

        OffsetDateTime now = OffsetDateTime.now();
        FeedItem older = persistItem("older title", now.minusDays(5), someInterest);
        FeedItem newer = persistItem("newer title", now.minusHours(1), someInterest);

        em.flush();
        em.clear();

        List<FeedItem> feed = feedItemRepository.findPersonalizedFeed(
                fresh.getId(), PageRequest.of(0, 20));

        // Both items match the user's interest, neither is hidden, no engagements
        // anywhere -> all weights are 0, ordering falls back to recency.
        assertThat(feed)
                .extracting(FeedItem::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    private FeedItem persistItem(String title, OffsetDateTime publishedAt, Interest interest) {
        Set<Interest> interests = new HashSet<>();
        interests.add(interest);
        FeedItem item = FeedItem.builder()
                .source("cold-start-test")
                .sourceId(title.replace(' ', '-') + "-" + UUID.randomUUID())
                .url("https://example.test/" + UUID.randomUUID())
                .title(title)
                .publishedAt(publishedAt)
                .metadata(new HashMap<>())
                .interests(interests)
                .build();
        em.persist(item);
        return item;
    }
}
