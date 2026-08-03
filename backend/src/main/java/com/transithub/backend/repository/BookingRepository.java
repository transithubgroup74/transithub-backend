package com.transithub.backend.repository;

import com.transithub.backend.model.Booking;
import com.transithub.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {
    List<Booking> findByUser(User user);
    java.util.Optional<Booking> findByQrCode(String qrCode);
    List<Booking> findBySchedule_Id(UUID scheduleId);

    // Custom (mock-bus) bookings don't have a Schedule, so a "trip instance" is
    // identified by operator + route + departure time. Used to keep two
    // passengers from taking the same seat on the same demo bus.
    List<Booking> findByOperatorAndOriginAndDestinationAndDepartsAt(
            String operator, String origin, String destination, String departsAt);
}