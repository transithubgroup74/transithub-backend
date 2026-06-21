package com.transithub.backend.repository;

import com.transithub.backend.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {
}