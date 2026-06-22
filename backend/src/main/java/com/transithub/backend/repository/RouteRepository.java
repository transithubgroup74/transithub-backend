package com.transithub.backend.repository;

import com.transithub.backend.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface RouteRepository extends JpaRepository<Route, UUID> {
    List<Route> findByOriginAndDestination(String origin, String destination);
}