package com.transithub.backend.repository;

import com.transithub.backend.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    @Query("SELECT s FROM Schedule s WHERE s.route.id = :routeId AND s.departsAt >= :from AND s.departsAt < :to")
    List<Schedule> findByRouteAndDate(@Param("routeId") UUID routeId,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    @Query("SELECT s FROM Schedule s WHERE s.route.origin = :origin AND s.route.destination = :destination AND s.departsAt >= :from AND s.departsAt < :to")
    List<Schedule> findByOriginDestinationAndDate(@Param("origin") String origin,
                                                  @Param("destination") String destination,
                                                  @Param("from") LocalDateTime from,
                                                  @Param("to") LocalDateTime to);
}
