package com.eventpulse.domain.event;

import com.eventpulse.domain.interest.Interest;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "events",
       uniqueConstraints = @UniqueConstraint(name = "events_source_unique",
                                             columnNames = {"source", "source_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String category;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Convert(converter = EventStatusConverter.class)
    @Column(nullable = false)
    private EventStatus status;

    @Column(nullable = false)
    private String source;

    @Column(name = "source_id", nullable = false)
    private String sourceId;

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "event_interests",
               joinColumns = @JoinColumn(name = "event_id"),
               inverseJoinColumns = @JoinColumn(name = "interest_id"))
    @Builder.Default
    private Set<Interest> interests = new HashSet<>();
}
