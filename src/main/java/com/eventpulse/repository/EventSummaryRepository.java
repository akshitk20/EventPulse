package com.eventpulse.repository;

import com.eventpulse.domain.event.EventSummary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventSummaryRepository extends JpaRepository<EventSummary, UUID> {
}
