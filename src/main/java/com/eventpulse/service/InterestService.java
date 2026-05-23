package com.eventpulse.service;

import com.eventpulse.domain.interest.Interest;
import com.eventpulse.domain.interest.InterestCategory;
import com.eventpulse.repository.InterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InterestService {

    private final InterestRepository interestRepository;

    public Optional<Interest> findBySlug(String slug) {
        return interestRepository.findBySlug(slug);
    }

    public Optional<Interest> findById(UUID id) {
        return interestRepository.findById(id);
    }

    public List<Interest> rootInterests() {
        return interestRepository.findByParentIsNull();
    }

    public List<Interest> childrenOf(UUID parentId) {
        return interestRepository.findByParent_Id(parentId);
    }

    public List<Interest> byCategory(InterestCategory category) {
        return interestRepository.findByCategory(category);
    }
}
