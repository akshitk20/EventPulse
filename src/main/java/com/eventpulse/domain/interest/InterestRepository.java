package com.eventpulse.domain.interest;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InterestRepository extends JpaRepository<Interest, UUID> {

    Optional<Interest> findBySlug(String slug);

    List<Interest> findByParentIsNull();

    List<Interest> findByParent_Id(UUID parentId);

    List<Interest> findByCategory(InterestCategory category);
}
