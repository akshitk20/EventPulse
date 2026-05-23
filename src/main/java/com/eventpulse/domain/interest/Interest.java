package com.eventpulse.domain.interest;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Self-referencing taxonomy: e.g. cricket -> ipl -> csk.
 * Parent can be null for top-level interests.
 */
@Entity
@Table(name = "interests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
public class Interest {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InterestCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Interest parent;
}
