package com.transithub.backend.repository;

import com.transithub.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    // Sign-in paths use this so an account isn't unreachable because it was
    // registered with different capitalisation than the user types.
    Optional<User> findByEmailIgnoreCase(String email);
}