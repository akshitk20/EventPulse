package com.eventpulse.repository;

import com.eventpulse.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByOauthProviderAndOauthSubject(String oauthProvider, String oauthSubject);

    Optional<User> findByEmail(String email);
}
