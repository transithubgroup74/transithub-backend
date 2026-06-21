package com.transithub.backend.config;

import com.transithub.backend.model.*;
import com.transithub.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final OperatorRepository operatorRepo;
    private final BusRepository busRepo;
    private final RouteRepository routeRepo;
    private final ScheduleRepository scheduleRepo;

    @Override
    public void run(String... args) {
        if (operatorRepo.count() > 0) return; // Already seeded

        // Operators
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

        // Buses
        Bus vipBus1 = busRepo.save(Bus.builder().operator(vip).plateNumber("GR-1234-22").capacity(50).model("VIP Executive").build());
        Bus vipBus2 = busRepo.save(Bus.builder().operator(vip).plateNumber("GR-5678-22").capacity(70).model("VIP Regular").build());
        Bus obbBus1 = busRepo.save(Bus.builder().operator(obb).plateNumber("AS-2233-21").capacity(60).model("OA Executive").build());
        Bus obbBus2 = busRepo.save(Bus.builder().operator(obb).plateNumber("AS-4455-21").capacity(70).model("OA Regular").build());
        Bus stcBus  = busRepo.save(Bus.builder().operator(stc).plateNumber("GT-9900-20").capacity(65).model("STC Regular").build());
        Bus kingBus = busRepo.save(Bus.builder().operator(kingdom).plateNumber("AE-7700-23").capacity(50).model("Kingdom Executive").build());

        // Routes
        Route kumasiAccra   = routeRepo.save(Route.builder().operator(vip).origin("Kumasi").destination("Accra").basePrice(new BigDecimal("80")).build());
        Route accraKumasi   = routeRepo.save(Route.builder().operator(vip).origin("Accra").destination("Kumasi").basePrice(new BigDecimal("80")).build());
        Route accraTamale   = routeRepo.save(Route.builder().operator(obb).origin("Accra").destination("Tamale").basePrice(new BigDecimal("120")).build());
        Route tamaleAccra   = routeRepo.save(Route.builder().operator(obb).origin("Tamale").destination("Accra").basePrice(new BigDecimal("120")).build());
        Route accraTagoradi = routeRepo.save(Route.builder().operator(stc).origin("Accra").destination("Takoradi").basePrice(new BigDecimal("60")).build());
        Route takoradiAccra = routeRepo.save(Route.builder().operator(stc).origin("Takoradi").destination("Accra").basePrice(new BigDecimal("60")).build());
        Route accraCape     = routeRepo.save(Route.builder().operator(kingdom).origin("Accra").destination("Cape Coast").basePrice(new BigDecimal("50")).build());
        Route capeAccra     = routeRepo.save(Route.builder().operator(kingdom).origin("Cape Coast").destination("Accra").basePrice(new BigDecimal("50")).build());
        Route accraBolga    = routeRepo.save(Route.builder().operator(obb).origin("Accra").destination("Bolgatanga").basePrice(new BigDecimal("140")).build());
        Route kumasiTamale  = routeRepo.save(Route.builder().operator(vip).origin("Kumasi").destination("Tamale").basePrice(new BigDecimal("100")).build());

        // Generate schedules for next 14 days
        LocalDate today = LocalDate.now();
        for (int day = 0; day < 14; day++) {
            LocalDate d = today.plusDays(day);

            // Kumasi → Accra: 06:00, 09:00, 13:00, 17:00
            scheduleRepo.save(Schedule.builder().route(kumasiAccra).bus(vipBus1).departsAt(d.atTime(6, 0)).build());
            scheduleRepo.save(Schedule.builder().route(kumasiAccra).bus(vipBus2).departsAt(d.atTime(9, 0)).build());
            scheduleRepo.save(Schedule.builder().route(kumasiAccra).bus(vipBus1).departsAt(d.atTime(13, 0)).build());
            scheduleRepo.save(Schedule.builder().route(kumasiAccra).bus(vipBus2).departsAt(d.atTime(17, 0)).build());

            // Accra → Kumasi: 06:00, 10:00, 14:00, 18:00
            scheduleRepo.save(Schedule.builder().route(accraKumasi).bus(vipBus1).departsAt(d.atTime(6, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraKumasi).bus(vipBus2).departsAt(d.atTime(10, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraKumasi).bus(vipBus1).departsAt(d.atTime(14, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraKumasi).bus(vipBus2).departsAt(d.atTime(18, 0)).build());

            // Accra → Tamale: 06:00, 14:00
            scheduleRepo.save(Schedule.builder().route(accraTamale).bus(obbBus1).departsAt(d.atTime(6, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraTamale).bus(obbBus2).departsAt(d.atTime(14, 0)).build());

            // Tamale → Accra: 06:00, 14:00
            scheduleRepo.save(Schedule.builder().route(tamaleAccra).bus(obbBus1).departsAt(d.atTime(6, 0)).build());
            scheduleRepo.save(Schedule.builder().route(tamaleAccra).bus(obbBus2).departsAt(d.atTime(14, 0)).build());

            // Accra → Takoradi: 07:00, 11:00, 15:00
            scheduleRepo.save(Schedule.builder().route(accraTagoradi).bus(stcBus).departsAt(d.atTime(7, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraTagoradi).bus(stcBus).departsAt(d.atTime(11, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraTagoradi).bus(stcBus).departsAt(d.atTime(15, 0)).build());

            // Takoradi → Accra: 06:00, 10:00, 14:00
            scheduleRepo.save(Schedule.builder().route(takoradiAccra).bus(stcBus).departsAt(d.atTime(6, 0)).build());
            scheduleRepo.save(Schedule.builder().route(takoradiAccra).bus(stcBus).departsAt(d.atTime(10, 0)).build());
            scheduleRepo.save(Schedule.builder().route(takoradiAccra).bus(stcBus).departsAt(d.atTime(14, 0)).build());

            // Accra → Cape Coast: 07:00, 12:00, 16:00
            scheduleRepo.save(Schedule.builder().route(accraCape).bus(kingBus).departsAt(d.atTime(7, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraCape).bus(kingBus).departsAt(d.atTime(12, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraCape).bus(kingBus).departsAt(d.atTime(16, 0)).build());

            // Cape Coast → Accra
            scheduleRepo.save(Schedule.builder().route(capeAccra).bus(kingBus).departsAt(d.atTime(6, 0)).build());
            scheduleRepo.save(Schedule.builder().route(capeAccra).bus(kingBus).departsAt(d.atTime(11, 0)).build());
            scheduleRepo.save(Schedule.builder().route(capeAccra).bus(kingBus).departsAt(d.atTime(15, 0)).build());

            // Accra → Bolgatanga: 05:00, 13:00
            scheduleRepo.save(Schedule.builder().route(accraBolga).bus(obbBus1).departsAt(d.atTime(5, 0)).build());
            scheduleRepo.save(Schedule.builder().route(accraBolga).bus(obbBus2).departsAt(d.atTime(13, 0)).build());

            // Kumasi → Tamale: 07:00, 15:00
            scheduleRepo.save(Schedule.builder().route(kumasiTamale).bus(vipBus1).departsAt(d.atTime(7, 0)).build());
            scheduleRepo.save(Schedule.builder().route(kumasiTamale).bus(vipBus2).departsAt(d.atTime(15, 0)).build());
        }

        System.out.println("TransitHub: Database seeded with operators, buses, routes and schedules.");
    }
}
