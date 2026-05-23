package com.eventpulse.domain.feed;

import com.eventpulse.domain.event.Event;
import com.eventpulse.domain.interest.Interest;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "feed_items",
       uniqueConstraints = @UniqueConstraint(name = "feed_items_source_unique",
                                             columnNames = {"source", "source_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedItem {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private String title;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "published_at")
    private OffsetDateTime publishedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_event_id")
    private Event relatedEvent;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @CreationTimestamp
    @Column(name = "fetched_at", nullable = false, updatable = false)
    private OffsetDateTime fetchedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "feed_item_interests",
               joinColumns = @JoinColumn(name = "feed_item_id"),
               inverseJoinColumns = @JoinColumn(name = "interest_id"))
    @Builder.Default
    private Set<Interest> interests = new HashSet<>();
}
