package com.eventpulse.domain.engagement;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_engagement")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEngagement {

    @EmbeddedId
    private UserEngagementId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("feedItemId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "feed_item_id")
    private FeedItem feedItem;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserEngagementId implements Serializable {

        @Column(name = "user_id")
        private UUID userId;

        @Column(name = "feed_item_id")
        private UUID feedItemId;

        @Convert(converter = EngagementActionConverter.class)
        @Column(name = "action", nullable = false)
        private EngagementAction action;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UserEngagementId that)) return false;
            return Objects.equals(userId, that.userId)
                && Objects.equals(feedItemId, that.feedItemId)
                && action == that.action;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, feedItemId, action);
        }
    }
}
