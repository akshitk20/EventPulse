package com.eventpulse.maintenance;

import com.eventpulse.domain.engagement.EngagementAction;
import com.eventpulse.domain.engagement.UserEngagement;
import com.eventpulse.domain.engagement.UserEngagement.UserEngagementId;
import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.user.User;
import com.eventpulse.repository.FeedItemRepository;
import com.eventpulse.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The retention job must reclaim old, ignored items <i>without</i> wiping
 * items the user has interacted with (save/hide/click/view). The FK from
 * {@code user_engagement} to {@code feed_items} is ON DELETE CASCADE, so a
 * naive {@code DELETE … WHERE fetched_at < cutoff} would silently destroy
 * the user's Saved view; the {@code NOT EXISTS} guard in the job is what
 * prevents that. This test pins that guard.
 */
@SpringBootTest
@ActiveProfiles("test")
class FeedItemRetentionJobTest {

    @Autowired FeedItemRetentionJob job;
    @Autowired FeedItemRepository feedItemRepository;
    @Autowired UserRepository userRepository;

    @PersistenceContext EntityManager em;

    @Test
    @Transactional
    void deletesOldUntouchedItemsButPreservesEngagedOnes() {
        User user = userRepository.save(User.builder()
                .email("retention-" + UUID.randomUUID() + "@test.local")
                .oauthProvider("google")
                .oauthSubject("retention-" + UUID.randomUUID())
                .build());

        // 100 days old, nobody touched it -> should be deleted
        FeedItem oldUntouched = persistItem(
                "old-untouched", OffsetDateTime.now().minusDays(100));

        // 100 days old, but the user saved it -> must survive
        FeedItem oldSaved = persistItem(
                "old-saved", OffsetDateTime.now().minusDays(100));
        em.persist(UserEngagement.builder()
                .id(new UserEngagementId(user.getId(), oldSaved.getId(), EngagementAction.SAVE))
                .user(user)
                .feedItem(oldSaved)
                .build());

        // 30 days old, untouched -> within window, should survive
        FeedItem recentUntouched = persistItem(
                "recent-untouched", OffsetDateTime.now().minusDays(30));

        em.flush();
        em.clear();

        job.purge();
        em.flush();
        em.clear();

        assertThat(feedItemRepository.findById(oldUntouched.getId()))
                .as("old item with no engagement should be purged")
                .isEmpty();
        assertThat(feedItemRepository.findById(oldSaved.getId()))
                .as("old item the user saved must survive")
                .isPresent();
        assertThat(feedItemRepository.findById(recentUntouched.getId()))
                .as("recent untouched item is within retention window")
                .isPresent();
    }

    private FeedItem persistItem(String slug, OffsetDateTime fetchedAt) {
        FeedItem item = FeedItem.builder()
                .source("retention-test")
                .sourceId(slug + "-" + UUID.randomUUID())
                .url("https://example.test/" + slug + "/" + UUID.randomUUID())
                .title(slug)
                .publishedAt(fetchedAt)
                .metadata(new HashMap<>())
                .build();
        em.persist(item);
        // fetched_at is set by @PrePersist to now(); override for the test.
        em.flush();
        em.createNativeQuery(
                "update feed_items set fetched_at = :ts where id = :id")
                .setParameter("ts", fetchedAt)
                .setParameter("id", item.getId())
                .executeUpdate();
        return item;
    }
}
