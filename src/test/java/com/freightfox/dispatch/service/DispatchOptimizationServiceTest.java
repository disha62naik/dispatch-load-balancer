package com.freightfox.dispatch.service;

import com.freightfox.dispatch.dto.AssignedOrderResponse;
import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.VehicleDispatchResponse;
import com.freightfox.dispatch.enums.OrderPriority;
import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;
import com.freightfox.dispatch.util.HaversineCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchOptimizationServiceTest {

    private final DispatchOptimizationService service =
            new DispatchOptimizationService(new DistanceCalculationService());

    private DeliveryOrder order(String id, double lat, double lon, int weight, OrderPriority priority) {
        return new DeliveryOrder(id, lat, lon, "Address " + id, BigDecimal.valueOf(weight), priority);
    }

    private Vehicle vehicle(String id, int capacity, double lat, double lon) {
        return new Vehicle(id, BigDecimal.valueOf(capacity), lat, lon, "Depot " + id);
    }

    private List<String> assignedIds(VehicleDispatchResponse vehicle) {
        return vehicle.assignedOrders().stream().map(AssignedOrderResponse::orderId).toList();
    }

    private VehicleDispatchResponse planFor(DispatchPlanResponse plan, String vehicleId) {
        return plan.dispatchPlan().stream().filter(v -> v.vehicleId().equals(vehicleId)).findFirst().orElseThrow();
    }

    @Test
    void highPriorityOrderIsAssignedBeforeLowWhenCapacityIsScarce() {
        List<DeliveryOrder> orders = List.of(
                order("ORD001", 0, 1, 15, OrderPriority.LOW),
                order("ORD002", 0, 1, 15, OrderPriority.HIGH));

        DispatchPlanResponse plan = service.optimize(orders, List.of(vehicle("VEH001", 20, 0, 0)));

        assertEquals(List.of("ORD002"), assignedIds(planFor(plan, "VEH001")));
        assertEquals("ORD001", plan.unassignedOrders().get(0).orderId());
    }

    @Test
    void mediumIsPlacedBeforeLowAndAfterHigh() {
        List<DeliveryOrder> orders = List.of(
                order("A", 0, 1, 10, OrderPriority.LOW),
                order("B", 0, 1, 10, OrderPriority.MEDIUM),
                order("C", 0, 1, 10, OrderPriority.HIGH));

        DispatchPlanResponse plan = service.optimize(orders, List.of(vehicle("VEH001", 20, 0, 0)));

        assertEquals(Set.of("C", "B"), new HashSet<>(assignedIds(planFor(plan, "VEH001"))));
        assertEquals("A", plan.unassignedOrders().get(0).orderId());
    }

    @Test
    void vehicleCapacityIsNeverExceeded() {
        List<DeliveryOrder> orders = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            orders.add(order("ORD%03d".formatted(i), 28.5 + i * 0.01, 77.2, 7 + i % 5, OrderPriority.values()[i % 3]));
        }
        List<Vehicle> vehicles = List.of(vehicle("VEH001", 40, 28.5, 77.2), vehicle("VEH002", 55, 28.7, 77.1));

        DispatchPlanResponse plan = service.optimize(orders, vehicles);

        assertTrue(planFor(plan, "VEH001").totalLoad() <= 40);
        assertTrue(planFor(plan, "VEH002").totalLoad() <= 55);
    }

    @Test
    void orderCanFillVehicleToExactCapacity() {
        DispatchPlanResponse plan = service.optimize(
                List.of(order("ORD001", 0, 1, 50, OrderPriority.HIGH)), List.of(vehicle("VEH001", 50, 0, 0)));

        assertEquals(1, plan.totalAssignedOrders());
        assertEquals(50.0, planFor(plan, "VEH001").totalLoad());
    }

    @Test
    void nearestFeasibleVehicleIsSelected() {
        List<Vehicle> vehicles = List.of(vehicle("VEH001", 100, 28.7041, 77.1025), vehicle("VEH002", 100, 28.5355, 77.3910));
        DeliveryOrder nearVeh002 = order("ORD001", 28.5400, 77.3900, 10, OrderPriority.HIGH);

        DispatchPlanResponse plan = service.optimize(List.of(nearVeh002), vehicles);

        assertEquals(List.of("ORD001"), assignedIds(planFor(plan, "VEH002")));
        assertTrue(planFor(plan, "VEH001").assignedOrders().isEmpty());
    }

    @Test
    void nearestVehicleIsSkippedWhenItCannotCarryTheOrder() {
        List<Vehicle> vehicles = List.of(vehicle("VEH001", 5, 0, 0), vehicle("VEH002", 100, 0, 5));
        DeliveryOrder order = order("ORD001", 0, 0.1, 10, OrderPriority.HIGH);

        DispatchPlanResponse plan = service.optimize(List.of(order), vehicles);

        assertEquals(List.of("ORD001"), assignedIds(planFor(plan, "VEH002")));
    }

    @Test
    void vehiclesAtTheSameLocationTieBreakOnLowerVehicleId() {
        List<Vehicle> vehicles = List.of(vehicle("VEH002", 100, 10, 10), vehicle("VEH001", 100, 10, 10));

        DispatchPlanResponse plan = service.optimize(List.of(order("ORD001", 11, 11, 10, OrderPriority.HIGH)), vehicles);

        assertEquals(List.of("ORD001"), assignedIds(planFor(plan, "VEH001")));
    }

    @Test
    void orderHeavierThanEveryVehicleIsReturnedAsUnassignedWithReason() {
        DispatchPlanResponse plan = service.optimize(
                List.of(order("ORD001", 0, 1, 500, OrderPriority.HIGH)),
                List.of(vehicle("VEH001", 100, 0, 0), vehicle("VEH002", 80, 0, 0)));

        assertEquals(0, plan.totalAssignedOrders());
        assertEquals(1, plan.totalUnassignedOrders());
        assertEquals("Package weight exceeds the capacity of every vehicle.", plan.unassignedOrders().get(0).reason());
    }

    @Test
    void orderThatFitsOnlyWhenVehicleIsEmptyGetsRemainingCapacityReason() {
        List<DeliveryOrder> orders = List.of(
                order("ORD001", 0, 1, 60, OrderPriority.HIGH),
                order("ORD002", 0, 1, 60, OrderPriority.MEDIUM));

        DispatchPlanResponse plan = service.optimize(orders, List.of(vehicle("VEH001", 100, 0, 0)));

        assertEquals("Insufficient remaining capacity on all vehicles.", plan.unassignedOrders().get(0).reason());
    }

    @Test
    void everyOrderIsEitherAssignedOrUnassignedExactlyOnce() {
        List<DeliveryOrder> orders = new ArrayList<>();
        for (int i = 1; i <= 30; i++) {
            orders.add(order("ORD%03d".formatted(i), 28.4 + i * 0.01, 77.0 + i * 0.01, 10 + i % 7 * 5, OrderPriority.values()[i % 3]));
        }
        List<Vehicle> vehicles = List.of(vehicle("VEH001", 100, 28.7, 77.1), vehicle("VEH002", 80, 28.5, 77.3),
                vehicle("VEH003", 120, 28.4, 77.0));

        DispatchPlanResponse plan = service.optimize(orders, vehicles);

        List<String> seen = new ArrayList<>();
        plan.dispatchPlan().forEach(v -> seen.addAll(assignedIds(v)));
        plan.unassignedOrders().forEach(u -> seen.add(u.orderId()));

        assertEquals(30, seen.size());
        assertEquals(30, new HashSet<>(seen).size());
        assertEquals(30, plan.totalAssignedOrders() + plan.totalUnassignedOrders());
    }

    @Test
    void noOrdersProducesEmptyVehiclePlans() {
        DispatchPlanResponse plan = service.optimize(List.of(), List.of(vehicle("VEH001", 100, 0, 0)));

        assertEquals(1, plan.dispatchPlan().size());
        assertTrue(plan.dispatchPlan().get(0).assignedOrders().isEmpty());
        assertEquals(0.0, plan.totalTravelDistance());
        assertTrue(plan.unassignedOrders().isEmpty());
    }

    @Test
    void noVehiclesLeavesEveryOrderUnassigned() {
        DispatchPlanResponse plan = service.optimize(List.of(order("ORD001", 0, 1, 10, OrderPriority.HIGH)), List.of());

        assertTrue(plan.dispatchPlan().isEmpty());
        assertEquals(1, plan.totalUnassignedOrders());
        assertEquals("No vehicles available.", plan.unassignedOrders().get(0).reason());
    }

    @Test
    void samePriorityOrdersAreAllocatedByOrderIdRegardlessOfInputOrder() {
        List<DeliveryOrder> orders = new ArrayList<>(List.of(
                order("ORD003", 0, 1, 10, OrderPriority.HIGH),
                order("ORD001", 0, 1, 10, OrderPriority.HIGH),
                order("ORD002", 0, 1, 10, OrderPriority.HIGH)));
        Collections.reverse(orders);

        DispatchPlanResponse plan = service.optimize(orders, List.of(vehicle("VEH001", 20, 0, 0)));

        assertEquals(Set.of("ORD001", "ORD002"), new HashSet<>(assignedIds(planFor(plan, "VEH001"))));
        assertEquals("ORD003", plan.unassignedOrders().get(0).orderId());
    }

    @Test
    void ordersAtIdenticalLocationsAreAllAssigned() {
        List<DeliveryOrder> orders = Stream.of("ORD001", "ORD002", "ORD003")
                .map(id -> order(id, 28.6139, 77.2090, 10, OrderPriority.MEDIUM)).toList();

        DispatchPlanResponse plan = service.optimize(orders, List.of(vehicle("VEH001", 100, 28.7041, 77.1025)));

        assertEquals(3, plan.totalAssignedOrders());
    }

    @Test
    void totalDistanceMatchesHaversineAndIsRoundedToTwoDecimals() {
        DispatchPlanResponse plan = service.optimize(
                List.of(order("ORD001", 0, 1, 10, OrderPriority.HIGH)), List.of(vehicle("VEH001", 100, 0, 0)));

        double expected = Math.round(HaversineCalculator.distanceKm(0, 0, 0, 1) * 100.0) / 100.0;
        assertEquals(expected, planFor(plan, "VEH001").totalDistance(), 1e-9);
        assertEquals(expected, plan.totalTravelDistance(), 1e-9);
    }

    @Test
    void routeFollowsNearestNeighbourSequenceFromVehicleStart() {
        List<DeliveryOrder> orders = List.of(
                order("ORD001", 0, 3, 10, OrderPriority.HIGH),
                order("ORD002", 0, 1, 10, OrderPriority.HIGH),
                order("ORD003", 0, 2, 10, OrderPriority.HIGH));

        DispatchPlanResponse plan = service.optimize(orders, List.of(vehicle("VEH001", 100, 0, 0)));

        VehicleDispatchResponse vehicle = planFor(plan, "VEH001");
        assertEquals(List.of("ORD002", "ORD003", "ORD001"), assignedIds(vehicle));
        assertEquals(List.of(1, 2, 3), vehicle.assignedOrders().stream().map(AssignedOrderResponse::sequence).toList());
        double expected = Math.round(HaversineCalculator.distanceKm(0, 0, 0, 3) * 100.0) / 100.0;
        assertEquals(expected, vehicle.totalDistance(), 0.011);
    }

    @Test
    void totalTravelDistanceEqualsSumOfVehicleDistances() {
        List<DeliveryOrder> orders = List.of(
                order("ORD001", 28.6139, 77.2090, 30, OrderPriority.HIGH),
                order("ORD002", 28.4595, 77.0266, 30, OrderPriority.HIGH));
        List<Vehicle> vehicles = List.of(vehicle("VEH001", 30, 28.7041, 77.1025), vehicle("VEH002", 30, 28.5355, 77.3910));

        DispatchPlanResponse plan = service.optimize(orders, vehicles);

        double sum = plan.dispatchPlan().stream().mapToDouble(VehicleDispatchResponse::totalDistance).sum();
        assertEquals(sum, plan.totalTravelDistance(), 0.001);
    }
}
