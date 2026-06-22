package com.transithub.backend.repository;

import com.transithub.backend.model.Operator;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface OperatorRepository extends JpaRepository<Operator, UUID> {
    Optional<Operator> findByEmail(String email);
}