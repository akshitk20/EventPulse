package com.eventpulse.domain.interest;

import com.eventpulse.domain.user.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_interests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInterest {

    @EmbeddedId
    private UserInterestId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @MapsId("interestId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interest_id")
    private Interest interest;

    @Column(nullable = false)
    private float weight = 1.0f;
}
