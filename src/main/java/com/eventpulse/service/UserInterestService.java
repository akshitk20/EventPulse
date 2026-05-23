package com.eventpulse.service;

import com.eventpulse.domain.interest.Interest;
import com.eventpulse.domain.interest.UserInterest;
import com.eventpulse.domain.interest.UserInterestId;
import com.eventpulse.domain.user.User;
import com.eventpulse.repository.InterestRepository;
import com.eventpulse.repository.UserInterestRepository;
import com.eventpulse.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserInterestService {

    private final UserInterestRepository userInterestRepository;
    private final UserRepository userRepository;
    private final InterestRepository interestRepository;

    @Transactional(readOnly = true)
    public List<UserInterest> forUser(UUID userId) {
        return userInterestRepository.findByUser_Id(userId);
    }

    @Transactional
    public UserInterest subscribe(UUID userId, UUID interestId, float weight) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        Interest interest = interestRepository.findById(interestId)
            .orElseThrow(() -> new IllegalArgumentException("interest not found: " + interestId));

        UserInterestId pk = new UserInterestId(userId, interestId);
        return userInterestRepository.findById(pk)
            .map(existing -> { existing.setWeight(weight); return existing; })
            .orElseGet(() -> userInterestRepository.save(UserInterest.builder()
                .id(pk)
                .user(user)
                .interest(interest)
                .weight(weight)
                .build()));
    }

    @Transactional
    public void unsubscribe(UUID userId, UUID interestId) {
        userInterestRepository.deleteByUser_IdAndInterest_Id(userId, interestId);
    }
}
