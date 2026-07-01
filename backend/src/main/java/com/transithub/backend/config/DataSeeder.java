package com.transithub.backend.config;

import com.transithub.backend.model.*;
import com.transithub.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
        seedExtraOperators();
        ensureFleetSize();
        refreshSchedules();
    }

    // A pool of Ghanaian names for topping up the driver roster (kept distinct
    // from the hand-seeded drivers above).
    private static final String[] DRIVER_NAMES = {
            "Kofi Adjei", "Ama Boakye", "Yaw Nyarko", "Akua Bonsu", "Kwesi Amankwah",
            "Abena Ofosu", "Kojo Wiredu", "Adwoa Yeboah", "Kwabena Osei", "Efua Acheampong",
            "Kwame Twum", "Akosua Larbi", "Fiifi Ansah", "Adjoa Kyei", "Ebo Mensa",
            "Esi Duah", "Yaw Opoku", "Ama Gyamfua", "Kojo Bediako", "Abena Aning",
            "Kwesi Nartey", "Adwoa Fosu", "Kofi Agyeman", "Akua Sekyere"
    };

    // Brings every operator up to a fuller fleet so the Buses/Drivers pages
    // (and each operator's scoped view) look realistic. Idempotent: only tops
    // up companies that are below target, so restarts add nothing.
    private void ensureFleetSize() {
        final int TARGET_BUSES = 5;
        final int TARGET_DRIVERS = 5;
        List<Bus> allBuses = busRepo.findAll();
        List<Driver> allDrivers = driverRepo.findAll();
        int busBase = allBuses.size();
        int drvBase = allDrivers.size();
        int newBuses = 0, newDrivers = 0;

        for (Operator op : operatorRepo.findAll()) {
            long busCount = allBuses.stream()
                    .filter(b -> b.getOperator() != null && b.getOperator().getId().equals(op.getId())).count();
            long drvCount = allDrivers.stream()
                    .filter(d -> d.getOperator() != null && d.getOperator().getId().equals(op.getId())).count();
            String shortName = op.getCompanyName().split(" ")[0];

            for (long i = busCount; i < TARGET_BUSES; i++) {
                int cap = (newBuses % 2 == 0) ? 45 : 60;
                busRepo.save(Bus.builder().operator(op)
                        .plateNumber("GH-" + (5000 + busBase + newBuses) + "-24")
                        .capacity(cap)
                        .model(shortName + (cap == 45 ? " Executive" : " Regular"))
                        .status("active").build());
                newBuses++;
            }
            for (long i = drvCount; i < TARGET_DRIVERS; i++) {
                int idx = drvBase + newDrivers;
                driverRepo.save(Driver.builder().operator(op)
                        .name(DRIVER_NAMES[idx % DRIVER_NAMES.length])
                        .phone(String.format("024%07d", 3000000 + idx))
                        .licenseNumber(String.format("GHA-DL-%04d", 3000 + idx))
                        .status((newDrivers % 5 == 4) ? "off-duty" : "active").build());
                newDrivers++;
            }
        }
        if (newBuses > 0 || newDrivers > 0) {
            System.out.println("TransitHub: Fleet topped up (+" + newBuses + " buses, +" + newDrivers + " drivers).");
        }
    }

    // Adds the two remaining companies that have staff accounts in the dashboard
    // but were missing from the backend seed — so per-company scoping shows real
    // routes/buses/drivers/schedules for their operators. Guarded per-company so
    // it also runs safely on the already-seeded production DB.
    private void seedExtraOperators() {
        if (findOperator("Night Rider Express") == null) {
            Operator nr = operatorRepo.save(Operator.builder()
                    .companyName("Night Rider Express").email("nightrider@transithub.com")
                    .passwordHash("seeded").plan("premium").momoAccount("0244000005").build());
            busRepo.save(Bus.builder().operator(nr).plateNumber("GN-3100-24").capacity(45).model("Night Rider Executive").status("active").build());
            busRepo.save(Bus.builder().operator(nr).plateNumber("GN-3200-24").capacity(60).model("Night Rider Regular").status("active").build());
            driverRepo.save(Driver.builder().operator(nr).name("Yusuf Alhassan").phone("0244111207").licenseNumber("GHA-DL-2207").status("active").build());
            driverRepo.save(Driver.builder().operator(nr).name("Mary Adjeiwaa").phone("0244111208").licenseNumber("GHA-DL-2208").status("active").build());
            routeRepo.save(Route.builder().operator(nr).origin("Accra").destination("Kumasi").basePrice(new BigDecimal("90")).build());
            routeRepo.save(Route.builder().operator(nr).origin("Accra").destination("Tamale").basePrice(new BigDecimal("130")).build());
        }
        if (findOperator("Metro Mass Transit") == null) {
            Operator mm = operatorRepo.save(Operator.builder()
                    .companyName("Metro Mass Transit").email("metromass@transithub.com")
                    .passwordHash("seeded").plan("starter").momoAccount("0244000006").build());
            busRepo.save(Bus.builder().operator(mm).plateNumber("GM-4100-19").capacity(70).model("Metro Standard").status("active").build());
            busRepo.save(Bus.builder().operator(mm).plateNumber("GM-4200-19").capacity(70).model("Metro Standard").status("active").build());
            driverRepo.save(Driver.builder().operator(mm).name("Emmanuel Nkrumah").phone("0244111209").licenseNumber("GHA-DL-2209").status("active").build());
            driverRepo.save(Driver.builder().operator(mm).name("Comfort Danso").phone("0244111210").licenseNumber("GHA-DL-2210").status("off-duty").build());
            routeRepo.save(Route.builder().operator(mm).origin("Accra").destination("Cape Coast").basePrice(new BigDecimal("55")).build());
            routeRepo.save(Route.builder().operator(mm).origin("Accra").destination("Koforidua").basePrice(new BigDecimal("40")).build());
        }
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

        LocalDate today  = LocalDate.now();
        LocalDate target = today.plusDays(30);

        List<Bus> buses = busRepo.findAll();
        List<Route> routes = routeRepo.findAll();
        if (buses.isEmpty() || routes.isEmpty()) return;

        // Furthest scheduled day PER ROUTE — so newly-added routes (which have
        // none yet) get filled in even when other routes already reach 30 days
        // ahead. Existing routes are idempotent (their startDay lands past the
        // target, so nothing is regenerated).
        Map<UUID, LocalDate> latestByRoute = new HashMap<>();
        for (Schedule s : scheduleRepo.findAll()) {
            if (s.getRoute() == null || s.getDepartsAt() == null) continue;
            UUID rid = s.getRoute().getId();
            LocalDate d = s.getDepartsAt().toLocalDate();
            latestByRoute.merge(rid, d, (a, b) -> a.isAfter(b) ? a : b);
        }

        for (Route route : routes) {
            if (route.getOperator() == null) continue;
            List<Bus> operatorBuses = buses.stream()
                    .filter(b -> b.getOperator() != null
                            && b.getOperator().getId().equals(route.getOperator().getId()))
                    .toList();
            if (operatorBuses.isEmpty()) continue;

            Bus bus1 = operatorBuses.get(0);
            Bus bus2 = operatorBuses.size() > 1 ? operatorBuses.get(1) : bus1;

            LocalDate latest = latestByRoute.get(route.getId());
            LocalDate startDay = (latest == null) ? today : latest.plusDays(1);
            for (LocalDate d = startDay; !d.isAfter(target); d = d.plusDays(1)) {
                scheduleRepo.save(Schedule.builder().route(route).bus(bus1).departsAt(d.atTime(6, 0)).build());
                scheduleRepo.save(Schedule.builder().route(route).bus(bus2).departsAt(d.atTime(10, 0)).build());
                scheduleRepo.save(Schedule.builder().route(route).bus(bus1).departsAt(d.atTime(14, 0)).build());
                scheduleRepo.save(Schedule.builder().route(route).bus(bus2).departsAt(d.atTime(18, 0)).build());
            }
        }

        System.out.println("TransitHub: Schedules refreshed per-route up to " + target);
    }
}
