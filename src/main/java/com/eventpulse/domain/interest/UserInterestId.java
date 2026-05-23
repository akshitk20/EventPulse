package com.eventpulse.domain.interest;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInterestId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "interest_id")
    private UUID interestId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserInterestId that)) return false;
        return Objects.equals(userId, that.userId) && Objects.equals(interestId, that.interestId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, interestId);
    }
}
