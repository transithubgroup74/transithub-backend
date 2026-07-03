package com.transithub.backend.repository;

import com.transithub.backend.model.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID> {
    Optional<Staff> findByStaffId(String staffId);
}
