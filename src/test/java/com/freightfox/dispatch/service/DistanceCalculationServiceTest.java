package com.freightfox.dispatch.service;

import com.freightfox.dispatch.enums.OrderPriority;
import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.util.HaversineCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistanceCalculationServiceTest {

    private final DistanceCalculationService service = new DistanceCalculationService();

    private DeliveryOrder orderAt(String id, double lat, double lon) {
        return new DeliveryOrder(id, lat, lon, "Test address", BigDecimal.TEN, OrderPriority.MEDIUM);
    }

    @Test
    void emptyRouteHasZeroDistance() {
        assertEquals(0.0, service.routeDistanceKm(28.6, 77.2, List.of()), 1e-9);
    }

    @Test
    void routeDistanceSumsEveryLegFromTheStart() {
        DeliveryOrder first = orderAt("A", 28.7041, 77.1025);
        DeliveryOrder second = orderAt("B", 28.5355, 77.3910);

        double expected = HaversineCalculator.distanceKm(28.6139, 77.2090, 28.7041, 77.1025)
                + HaversineCalculator.distanceKm(28.7041, 77.1025, 28.5355, 77.3910);

        assertEquals(expected, service.routeDistanceKm(28.6139, 77.2090, List.of(first, second)), 1e-9);
    }

    @Test
    void routeDoesNotIncludeReturnToStart() {
        DeliveryOrder only = orderAt("A", 0, 1);
        assertEquals(HaversineCalculator.distanceKm(0, 0, 0, 1), service.routeDistanceKm(0, 0, List.of(only)), 1e-9);
    }
}
