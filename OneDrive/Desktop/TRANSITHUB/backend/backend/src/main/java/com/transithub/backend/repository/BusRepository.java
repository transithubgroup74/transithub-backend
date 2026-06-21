package com.transithub.backend.repository;

import com.transithub.backend.model.Bus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BusRepository extends JpaRepository<Bus, UUID> {
}