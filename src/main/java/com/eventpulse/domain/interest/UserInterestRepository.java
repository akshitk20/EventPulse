package com.eventpulse.domain.interest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserInterestRepository extends JpaRepository<UserInterest, UserInterestId> {

    List<UserInterest> findByUser_Id(UUID userId);

    void deleteByUser_IdAndInterest_Id(UUID userId, UUID interestId);
}
