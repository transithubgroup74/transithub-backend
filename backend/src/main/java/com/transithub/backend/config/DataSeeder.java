package com.transithub.backend.config;

import com.transithub.backend.model.*;
import com.transithub.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final OperatorRepository operatorRepo;
    private final BusRepository busRepo;
    private final RouteRepository routeRepo;
    private final ScheduleRepository scheduleRepo;
    private final DriverRepository driverRepo;

    @Override
    public void run(String... args) {
        if (operatorRepo.count() == 0) {
            seedStaticData();
        }
        // Independent guards so these also populate on an already-seeded DB
        // (the production DB already has operators, so seedStaticData is skipped).
        backfillBusStatus();
        if (driverRepo.count() == 0) {
            seedDrivers();
        }
        refreshSchedules();
    }

    // Existing buses pre-date the `status` column — default them to "active".
    private void backfillBusStatus() {
        List<Bus> buses = busRepo.findAll();
        for (Bus b : buses) {
            if (b.getStatus() == null || b.getStatus().isBlank()) {
                b.setStatus("active");
                busRepo.save(b);
            }
        }
    }

    private Operator findOperator(String companyName) {
        return operatorRepo.findAll().stream()
                .filter(o -> companyName.equalsIgnoreCase(o.getCompanyName()))
                .findFirst().orElse(null);
    }

    private void seedDrivers() {
        Operator vip = findOperator("VIP Jeoun");
        Operator obb = findOperator("OA Express");
        Operator stc = findOperator("STC Coaches");
        Operator kingdom = findOperator("Kingdom Transport");

        if (vip != null) {
            driverRepo.save(Driver.builder().operator(vip).name("Kwame Mensah").phone("0244111201").licenseNumber("GHA-DL-2201").status("active").build());
            driverRepo.save(Driver.builder().operator(vip).name("Yaw Boateng").phone("0244111202").licenseNumber("GHA-DL-2202").status("active").build());
        }
        if (obb != null) {
            driverRepo.save(Driver.builder().operator(obb).name("Kofi Owusu").phone("0244111203").licenseNumber("GHA-DL-2203").status("active").build());
            driverRepo.save(Driver.builder().operator(obb).name("Abena Sarpong").phone("0244111204").licenseNumber("GHA-DL-2204").status("off-duty").build());
        }
        if (stc != null) {
            driverRepo.save(Driver.builder().operator(stc).name("Ibrahim Mohammed").phone("0244111205").licenseNumber("GHA-DL-2205").status("active").build());
        }
        if (kingdom != null) {
            driverRepo.save(Driver.builder().operator(kingdom).name("Daniel Asante").phone("0244111206").licenseNumber("GHA-DL-2206").status("active").build());
        }
        System.out.println("TransitHub: Drivers seeded.");
    }

    private void seedStaticData() {
        Operator vip = operatorRepo.save(Operator.builder()
                .companyName("VIP Jeoun").email("vip@transithub.com")
                .passwordHash("seeded").plan("premium").momoAccount("0244000001").build());

        Operator obb = operatorRepo.save(Operator.builder()
                .companyName("OA Express").email("oa@transithub.com")
                .passwordHash("seeded").plan("premium").momoAccount("0244000002").build());

        Operator stc = operatorRepo.save(Operator.builder()
                .companyName("STC Coaches").email("stc@transithub.com")
                .passwordHash("seeded").plan("starter").momoAccount("0244000003").build());

        Operator kingdom = operatorRepo.save(Operator.builder()
                .companyName("Kingdom Transport").email("kingdom@transithub.com")
                .passwordHash("seeded").plan("starter").momoAccount("0244000004").build());

        busRepo.save(Bus.builder().operator(vip).plateNumber("GR-1234-22").capacity(50).model("VIP Executive").build());
        busRepo.save(Bus.builder().operator(vip).plateNumber("GR-5678-22").capacity(70).model("VIP Regular").build());
        busRepo.save(Bus.builder().operator(obb).plateNumber("AS-2233-21").capacity(60).model("OA Executive").build());
        busRepo.save(Bus.builder().operator(obb).plateNumber("AS-4455-21").capacity(70).model("OA Regular").build());
        busRepo.save(Bus.builder().operator(stc).plateNumber("GT-9900-20").capacity(65).model("STC Regular").build());
        busRepo.save(Bus.builder().operator(kingdom).plateNumber("AE-7700-23").capacity(50).model("Kingdom Executive").build());

        routeRepo.save(Route.builder().operator(vip).origin("Kumasi").destination("Accra").basePrice(new BigDecimal("80")).build());
        routeRepo.save(Route.builder().operator(vip).origin("Accra").destination("Kumasi").basePrice(new BigDecimal("80")).build());
        routeRepo.save(Route.builder().operator(obb).origin("Accra").destination("Tamale").basePrice(new BigDecimal("120")).build());
        routeRepo.save(Route.builder().operator(obb).origin("Tamale").destination("Accra").basePrice(new BigDecimal("120")).build());
        routeRepo.save(Route.builder().operator(stc).origin("Accra").destination("Takoradi").basePrice(new BigDecimal("60")).build());
        routeRepo.save(Route.builder().operator(stc).origin("Takoradi").destination("Accra").basePrice(new BigDecimal("60")).build());
        routeRepo.save(Route.builder().operator(kingdom).origin("Accra").destination("Cape Coast").basePrice(new BigDecimal("50")).build());
        routeRepo.save(Route.builder().operator(kingdom).origin("Cape Coast").destination("Accra").basePrice(new BigDecimal("50")).build());
        routeRepo.save(Route.builder().operator(obb).origin("Accra").destination("Bolgatanga").basePrice(new BigDecimal("140")).build());
        routeRepo.save(Route.builder().operator(vip).origin("Kumasi").destination("Tamale").basePrice(new BigDecimal("100")).build());

        System.out.println("TransitHub: Static data seeded.");
    }

    private void refreshSchedules() {
        // Delete schedules older than yesterday to keep the DB clean
        LocalDate yesterday = LocalDate.now().minusDays(1);
        scheduleRepo.deleteByDepartsAtBefore(yesterday.atStartOfDay());

        // Find the furthest future schedule already stored
        LocalDate latestSeeded = scheduleRepo.findMaxDepartsAt()
                .map(dt -> dt.toLocalDate())
                .orElse(LocalDate.now().minusDays(1));

        LocalDate target = LocalDate.now().plusDays(30);
        if (!latestSeeded.isBefore(target)) return; // Already have 30 days ahead

        List<Bus> buses = busRepo.findAll();
        List<Route> routes = routeRepo.findAll();
        if (buses.isEmpty() || routes.isEmpty()) return;

        // Build schedules from latestSeeded+1 up to 30 days ahead
        LocalDate startDay = latestSeeded.plusDays(1);
        for (LocalDate d = startDay; !d.isAfter(target); d = d.plusDays(1)) {
            for (Route route : routes) {
                List<Bus> operatorBuses = buses.stream()
                        .filter(b -> b.getOperator().getId().equals(route.getOperator().getId()))
                        .toList();
                if (operatorBuses.isEmpty()) continue;

                Bus bus1 = operatorBuses.get(0);
                Bus bus2 = operatorBuses.size() > 1 ? operatorBuses.get(1) : bus1;

                scheduleRepo.save(Schedule.builder().route(route).bus(bus1).departsAt(d.atTime(6, 0)).build());
                scheduleRepo.save(Schedule.builder().route(route).bus(bus2).departsAt(d.atTime(10, 0)).build());
                scheduleRepo.save(Schedule.builder().route(route).bus(bus1).departsAt(d.atTime(14, 0)).build());
                scheduleRepo.save(Schedule.builder().route(route).bus(bus2).departsAt(d.atTime(18, 0)).build());
            }
        }

        System.out.println("TransitHub: Schedules refreshed up to " + target);
    }
}
