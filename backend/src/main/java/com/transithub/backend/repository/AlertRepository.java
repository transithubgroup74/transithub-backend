package com.transithub.backend.repository;

import com.transithub.backend.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface AlertRepository extends JpaRepository<Alert, UUID> {
    List<Alert> findTop50ByOrderByCreatedAtDesc();
}
