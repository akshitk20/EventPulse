package com.eventpulse.domain.social;

import com.eventpulse.domain.interest.Interest;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Maps an interest to source-specific search terms.
 * Example rows for interest 'ipl':
 *   (ipl, reddit,  "r/cricket")
 *   (ipl, reddit,  "ipl 2026")
 *   (ipl, bluesky, "#IPL2026")
 *   (ipl, hn,      "IPL")
 */
@Entity
@Table(name = "topic_aliases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicAlias {

    @EmbeddedId
    private TopicAliasId id;

    @MapsId("interestId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interest_id")
    private Interest interest;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TopicAliasId implements Serializable {

        @Column(name = "interest_id")
        private UUID interestId;

        @Column(name = "source", nullable = false)
        private String source;

        @Column(name = "query", nullable = false)
        private String query;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof TopicAliasId that)) return false;
            return Objects.equals(interestId, that.interestId)
                && Objects.equals(source, that.source)
                && Objects.equals(query, that.query);
        }

        @Override
        public int hashCode() {
            return Objects.hash(interestId, source, query);
        }
    }
}
