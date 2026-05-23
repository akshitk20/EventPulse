package com.eventpulse.service;

import com.eventpulse.domain.engagement.EngagementAction;
import com.eventpulse.domain.engagement.UserEngagement;
import com.eventpulse.domain.engagement.UserEngagement.UserEngagementId;
import com.eventpulse.domain.feed.FeedItem;
import com.eventpulse.domain.user.User;
import com.eventpulse.repository.FeedItemRepository;
import com.eventpulse.repository.UserEngagementRepository;
import com.eventpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EngagementService {

    private final UserEngagementRepository engagementRepository;
    private final UserRepository userRepository;
    private final FeedItemRepository feedItemRepository;

    @Transactional(readOnly = true)
    public List<UserEngagement> forUser(UUID userId) {
        return engagementRepository.findByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public List<UserEngagement> forUserByAction(UUID userId, EngagementAction action) {
        return engagementRepository.findByUser_IdAndId_Action(userId, action);
    }

    @Transactional(readOnly = true)
    public long countAction(UUID feedItemId, EngagementAction action) {
        return engagementRepository.countByFeedItem_IdAndId_Action(feedItemId, action);
    }

    /**
     * Record an engagement event. Idempotent: re-recording the same (user, item, action)
     * is a no-op because the composite PK includes the action.
     */
    @Transactional
    public UserEngagement record(UUID userId, UUID feedItemId, EngagementAction action) {
        UserEngagementId pk = new UserEngagementId(userId, feedItemId, action);
        return engagementRepository.findById(pk).orElseGet(() -> {
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
            FeedItem item = feedItemRepository.findById(feedItemId)
                .orElseThrow(() -> new IllegalArgumentException("feed item not found: " + feedItemId));
            return engagementRepository.save(UserEngagement.builder()
                .id(pk)
                .user(user)
                .feedItem(item)
                .build());
        });
    }
}
