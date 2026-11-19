package com.freightfox.dispatch.service;

import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Mutable working state for one vehicle while the optimiser is allocating orders. */
class VehicleAllocation {

    private final Vehicle vehicle;
    private final List<DeliveryOrder> orders = new ArrayList<>();
    private BigDecimal load = BigDecimal.ZERO;

    VehicleAllocation(Vehicle vehicle) {
        this.vehicle = vehicle;
    }

    Vehicle getVehicle() {
        return vehicle;
    }

    List<DeliveryOrder> getOrders() {
        return orders;
    }

    BigDecimal getLoad() {
        return load;
    }

    BigDecimal remainingCapacity() {
        return vehicle.getCapacity().subtract(load);
    }

    boolean canCarry(DeliveryOrder order) {
        return order.getPackageWeight().compareTo(remainingCapacity()) <= 0;
    }

    void add(DeliveryOrder order) {
        orders.add(order);
        load = load.add(order.getPackageWeight());
    }

    /** Latitude of the last assigned stop, or the vehicle's start if nothing is assigned yet. */
    double lastLatitude() {
        return orders.isEmpty() ? vehicle.getCurrentLatitude() : orders.get(orders.size() - 1).getLatitude();
    }

    double lastLongitude() {
        return orders.isEmpty() ? vehicle.getCurrentLongitude() : orders.get(orders.size() - 1).getLongitude();
    }
}
