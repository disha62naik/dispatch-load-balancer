package com.freightfox.dispatch.service;

import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.OrderRequest;
import com.freightfox.dispatch.dto.VehicleRequest;
import com.freightfox.dispatch.exception.InvalidDispatchRequestException;
import com.freightfox.dispatch.exception.ResourceNotFoundException;
import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;
import com.freightfox.dispatch.repository.OrderRepository;
import com.freightfox.dispatch.repository.VehicleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DispatchService {

    private final OrderRepository orderRepository;
    private final VehicleRepository vehicleRepository;
    private final DispatchOptimizationService optimizationService;

    public DispatchService(OrderRepository orderRepository, VehicleRepository vehicleRepository,
                           DispatchOptimizationService optimizationService) {
        this.orderRepository = orderRepository;
        this.vehicleRepository = vehicleRepository;
        this.optimizationService = optimizationService;
    }

    /** Inserts new orders and updates existing ones (matched by orderId): one lookup query, one batched save. */
    @Transactional
    public void saveOrders(List<OrderRequest> requests) {
        List<String> orderIds = requests.stream().map(OrderRequest::orderId).toList();
        rejectDuplicates(orderIds, "order");

        Map<String, DeliveryOrder> existing = orderRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(DeliveryOrder::getOrderId, Function.identity()));

        List<DeliveryOrder> toSave = requests.stream().map(request -> {
            DeliveryOrder order = existing.get(request.orderId());
            if (order == null) {
                return new DeliveryOrder(request.orderId(), request.latitude(), request.longitude(),
                        request.address(), request.packageWeight(), request.priority());
            }
            order.update(request.latitude(), request.longitude(), request.address(),
                    request.packageWeight(), request.priority());
            return order;
        }).toList();

        orderRepository.saveAll(toSave);
    }

    /** Inserts new vehicles and updates existing ones (matched by vehicleId). */
    @Transactional
    public void saveVehicles(List<VehicleRequest> requests) {
        List<String> vehicleIds = requests.stream().map(VehicleRequest::vehicleId).toList();
        rejectDuplicates(vehicleIds, "vehicle");

        Map<String, Vehicle> existing = vehicleRepository.findByVehicleIdIn(vehicleIds).stream()
                .collect(Collectors.toMap(Vehicle::getVehicleId, Function.identity()));

        List<Vehicle> toSave = requests.stream().map(request -> {
            Vehicle vehicle = existing.get(request.vehicleId());
            if (vehicle == null) {
                return new Vehicle(request.vehicleId(), request.capacity(), request.currentLatitude(),
                        request.currentLongitude(), request.currentAddress());
            }
            vehicle.update(request.capacity(), request.currentLatitude(),
                    request.currentLongitude(), request.currentAddress());
            return vehicle;
        }).toList();

        vehicleRepository.saveAll(toSave);
    }

    @Transactional(readOnly = true)
    public DispatchPlanResponse generatePlan() {
        List<DeliveryOrder> orders = orderRepository.findAll();
        if (orders.isEmpty()) {
            throw new ResourceNotFoundException("No delivery orders found. Submit orders before requesting a plan.");
        }
        List<Vehicle> vehicles = vehicleRepository.findAll();
        if (vehicles.isEmpty()) {
            throw new ResourceNotFoundException("No vehicles found. Submit vehicles before requesting a plan.");
        }
        return optimizationService.optimize(orders, vehicles);
    }

    private void rejectDuplicates(List<String> ids, String entityName) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = ids.stream()
                .filter(id -> !seen.add(id))
                .collect(Collectors.toCollection(TreeSet::new));
        if (!duplicates.isEmpty()) {
            throw new InvalidDispatchRequestException(
                    "Duplicate " + entityName + " IDs in request: " + String.join(", ", duplicates));
        }
    }
}
