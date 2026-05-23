package com.eventpulse.service;

import com.eventpulse.domain.event.Event;
import com.eventpulse.domain.event.EventStatus;
import com.eventpulse.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public Optional<Event> findById(UUID id) {
        return eventRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<Event> startingBetween(OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        return eventRepository.findStartingBetween(from, to, pageable);
    }

    @Transactional(readOnly = true)
    public List<Event> relevantForUser(UUID userId, Pageable pageable) {
        return eventRepository.findRelevantForUser(userId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Event> byStatus(EventStatus status) {
        return eventRepository.findByStatus(status);
    }

    /**
     * Upsert by (source, sourceId): if an event from that source already exists,
     * apply the mutator to update it; otherwise save the supplied event.
     * Used by aggregator clients (TheSportsDB, TMDB, etc.) on each refresh.
     */
    @Transactional
    public Event upsertBySource(Event candidate, Consumer<Event> updateExisting) {
        return eventRepository.findBySourceAndSourceId(candidate.getSource(), candidate.getSourceId())
            .map(existing -> { updateExisting.accept(existing); return existing; })
            .orElseGet(() -> eventRepository.save(candidate));
    }
}
