package com.eventpulse.repository;

import com.eventpulse.domain.interest.UserInterest;
import com.eventpulse.domain.interest.UserInterestId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterestId> {

    List<UserInterest> findByUser_Id(UUID userId);

    void deleteByUser_IdAndInterest_Id(UUID userId, UUID interestId);
}
