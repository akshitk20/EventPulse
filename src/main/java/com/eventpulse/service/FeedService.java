package com.eventpulse.service;

import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.repository.FeedItemRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class FeedService {

    private final FeedItemRepository feedItemRepository;

    @Transactional(readOnly = true)
    public List<FeedItem> personalizedFeed(UUID userId, Pageable pageable) {
        List<FeedItem> items = feedItemRepository.findPersonalizedFeed(userId, pageable);
        items.forEach(i -> Hibernate.initialize(i.getInterests()));
        return items;
    }

    @Transactional(readOnly = true)
    public List<FeedItem> searchPersonalizedFeed(
            UUID userId, String q, String source, UUID interestId, Pageable pageable) {
        // Empty-string sentinel for source — same reason as before: a null String
        // bind types as bytea on Postgres and breaks the equality comparison.
        String sNorm = (source == null || source.isBlank()) ? "" : source;
        boolean hasQuery = q != null && !q.isBlank();
        // Branch on q: with a query we use Postgres FTS (ts_rank_cd ordering,
        // stemming, stop-word handling); without one we fall back to the chronological
        // filter query that doesn't need ranking.
        List<FeedItem> items = hasQuery
            ? feedItemRepository.ftsSearchPersonalizedFeed(
                  userId, q.trim(), sNorm, interestId, pageable)
            : feedItemRepository.filterPersonalizedFeed(
                  userId, sNorm, interestId, pageable);
        items.forEach(i -> Hibernate.initialize(i.getInterests()));
        return items;
    }

    @Transactional(readOnly = true)
    public List<FeedItem> savedFeed(UUID userId, Pageable pageable) {
        List<FeedItem> items = feedItemRepository.findSavedByUser(userId, pageable);
        items.forEach(i -> Hibernate.initialize(i.getInterests()));
        return items;
    }

    @Transactional(readOnly = true)
    public List<FeedItem> hiddenFeed(UUID userId, Pageable pageable) {
        List<FeedItem> items = feedItemRepository.findHiddenByUser(userId, pageable);
        items.forEach(i -> Hibernate.initialize(i.getInterests()));
        return items;
    }

    @Transactional(readOnly = true)
    public Optional<FeedItem> findById(UUID id) {
        return feedItemRepository.findById(id)
            .map(item -> {
                Hibernate.initialize(item.getInterests());
                if (item.getRelatedEvent() != null) {
                    Hibernate.initialize(item.getRelatedEvent());
                }
                return item;
            });
    }

    /**
     * Upsert by (source, sourceId): aggregator clients call this for each fetched item.
     */
    @Transactional
    public FeedItem upsertBySource(FeedItem candidate, Consumer<FeedItem> updateExisting) {
        return feedItemRepository.findBySourceAndSourceId(candidate.getSource(), candidate.getSourceId())
            .map(existing -> { updateExisting.accept(existing); return existing; })
            .orElseGet(() -> feedItemRepository.save(candidate));
    }
}
