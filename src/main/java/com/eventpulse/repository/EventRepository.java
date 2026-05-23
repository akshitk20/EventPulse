package com.eventpulse.repository;

import com.eventpulse.domain.event.Event;
import com.eventpulse.domain.event.EventStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    Optional<Event> findBySourceAndSourceId(String source, String sourceId);

    List<Event> findByStatus(EventStatus status);

    @Query("""
        select e from Event e
        where e.startsAt >= :from and e.startsAt < :to
        order by e.startsAt asc
    """)
    List<Event> findStartingBetween(@Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to,
                                    Pageable pageable);

    /**
     * Events relevant to a user: any event tagged with one of the user's interests.
     * Joins through event_interests + user_interests.
     */
    @Query("""
        select distinct e from Event e
        join e.interests i
        join UserInterest ui on ui.interest = i
        where ui.user.id = :userId
          and e.status in (com.eventpulse.domain.event.EventStatus.LIVE,
                           com.eventpulse.domain.event.EventStatus.UPCOMING)
        order by e.startsAt asc
    """)
    List<Event> findRelevantForUser(@Param("userId") UUID userId, Pageable pageable);
}
