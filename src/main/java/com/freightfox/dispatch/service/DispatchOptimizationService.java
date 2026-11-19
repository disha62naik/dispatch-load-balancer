package com.freightfox.dispatch.service;

import com.freightfox.dispatch.dto.AssignedOrderResponse;
import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.UnassignedOrderResponse;
import com.freightfox.dispatch.dto.VehicleDispatchResponse;
import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Priority-aware greedy allocation followed by nearest-neighbour route ordering.
 * This is a heuristic: it is fast and deterministic but does not guarantee the globally shortest total distance.
 */
@Service
public class DispatchOptimizationService {

    private static final Comparator<DeliveryOrder> ALLOCATION_ORDER =
            Comparator.comparing(DeliveryOrder::getPriority).thenComparing(DeliveryOrder::getOrderId);

    private final DistanceCalculationService distanceService;

    public DispatchOptimizationService(DistanceCalculationService distanceService) {
        this.distanceService = distanceService;
    }

    public DispatchPlanResponse optimize(List<DeliveryOrder> orders, List<Vehicle> vehicles) {
        List<VehicleAllocation> allocations = vehicles.stream()
                .sorted(Comparator.comparing(Vehicle::getVehicleId))
                .map(VehicleAllocation::new)
                .toList();

        List<UnassignedOrderResponse> unassigned = new ArrayList<>();
        for (DeliveryOrder order : orders.stream().sorted(ALLOCATION_ORDER).toList()) {
            VehicleAllocation chosen = selectVehicle(order, allocations);
            if (chosen == null) {
                unassigned.add(toUnassigned(order, allocations));
            } else {
                chosen.add(order);
            }
        }

        List<VehicleDispatchResponse> vehiclePlans = allocations.stream().map(this::toVehicleResponse).toList();
        int assignedCount = vehiclePlans.stream().mapToInt(plan -> plan.assignedOrders().size()).sum();
        double totalDistance = vehiclePlans.stream()
                .map(plan -> BigDecimal.valueOf(plan.totalDistance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .doubleValue();

        return new DispatchPlanResponse(vehiclePlans, unassigned, assignedCount, unassigned.size(), round(totalDistance));
    }

    /**
     * Picks the vehicle with enough spare capacity and the smallest extra distance, measured from
     * its last assigned stop (or its start point) to the order. Vehicles are pre-sorted by ID, and
     * only a strictly smaller distance replaces the current choice, so ties go to the lower vehicle ID.
     */
    private VehicleAllocation selectVehicle(DeliveryOrder order, List<VehicleAllocation> allocations) {
        VehicleAllocation best = null;
        double bestDistance = Double.MAX_VALUE;
        for (VehicleAllocation allocation : allocations) {
            if (!allocation.canCarry(order)) {
                continue;
            }
            double extraDistance = distanceService.distanceKm(allocation.lastLatitude(), allocation.lastLongitude(),
                    order.getLatitude(), order.getLongitude());
            if (extraDistance < bestDistance) {
                best = allocation;
                bestDistance = extraDistance;
            }
        }
        return best;
    }

    private VehicleDispatchResponse toVehicleResponse(VehicleAllocation allocation) {
        Vehicle vehicle = allocation.getVehicle();
        List<DeliveryOrder> route = sequenceByNearestNeighbour(vehicle, allocation.getOrders());
        double distance = distanceService.routeDistanceKm(
                vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude(), route);
        List<AssignedOrderResponse> stops = IntStream.range(0, route.size())
                .mapToObj(index -> toAssigned(route.get(index), index + 1))
                .toList();

        return new VehicleDispatchResponse(vehicle.getVehicleId(), vehicle.getCurrentLatitude(),
                vehicle.getCurrentLongitude(), vehicle.getCurrentAddress(),
                allocation.getLoad().doubleValue(), round(distance), stops);
    }

    /** Repeatedly visits the closest unvisited order. Ties keep the earlier (higher-priority) order. */
    private List<DeliveryOrder> sequenceByNearestNeighbour(Vehicle vehicle, List<DeliveryOrder> assigned) {
        List<DeliveryOrder> remaining = new ArrayList<>(assigned);
        List<DeliveryOrder> route = new ArrayList<>(assigned.size());
        double currentLat = vehicle.getCurrentLatitude();
        double currentLon = vehicle.getCurrentLongitude();

        while (!remaining.isEmpty()) {
            DeliveryOrder nearest = remaining.get(0);
            double nearestDistance = distanceService.distanceKm(currentLat, currentLon,
                    nearest.getLatitude(), nearest.getLongitude());
            for (DeliveryOrder candidate : remaining) {
                double distance = distanceService.distanceKm(currentLat, currentLon,
                        candidate.getLatitude(), candidate.getLongitude());
                if (distance < nearestDistance) {
                    nearest = candidate;
                    nearestDistance = distance;
                }
            }
            remaining.remove(nearest);
            route.add(nearest);
            currentLat = nearest.getLatitude();
            currentLon = nearest.getLongitude();
        }
        return route;
    }

    private AssignedOrderResponse toAssigned(DeliveryOrder order, int sequence) {
        return new AssignedOrderResponse(sequence, order.getOrderId(), order.getLatitude(), order.getLongitude(),
                order.getAddress(), order.getPackageWeight().doubleValue(), order.getPriority());
    }

    private UnassignedOrderResponse toUnassigned(DeliveryOrder order, List<VehicleAllocation> allocations) {
        return new UnassignedOrderResponse(order.getOrderId(), order.getAddress(),
                order.getPackageWeight().doubleValue(), order.getPriority(), unassignedReason(order, allocations));
    }

    private String unassignedReason(DeliveryOrder order, List<VehicleAllocation> allocations) {
        if (allocations.isEmpty()) {
            return "No vehicles available.";
        }
        boolean fitsAnyEmptyVehicle = allocations.stream()
                .anyMatch(allocation -> order.getPackageWeight().compareTo(allocation.getVehicle().getCapacity()) <= 0);
        return fitsAnyEmptyVehicle
                ? "Insufficient remaining capacity on all vehicles."
                : "Package weight exceeds the capacity of every vehicle.";
    }

    private double round(double kilometres) {
        return BigDecimal.valueOf(kilometres).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
