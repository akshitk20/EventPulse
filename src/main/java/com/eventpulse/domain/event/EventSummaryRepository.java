package com.eventpulse.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventSummaryRepository extends JpaRepository<EventSummary, UUID> {
}
