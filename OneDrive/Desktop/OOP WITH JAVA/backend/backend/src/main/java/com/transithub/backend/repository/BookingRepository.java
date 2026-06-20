package com.transithub.backend.repository;

import com.transithub.backend.model.Booking;
import com.transithub.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByUser(User user);
}