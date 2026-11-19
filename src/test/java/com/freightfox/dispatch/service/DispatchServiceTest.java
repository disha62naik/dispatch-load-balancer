package com.freightfox.dispatch.service;

import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.OrderRequest;
import com.freightfox.dispatch.dto.VehicleRequest;
import com.freightfox.dispatch.enums.OrderPriority;
import com.freightfox.dispatch.exception.InvalidDispatchRequestException;
import com.freightfox.dispatch.exception.ResourceNotFoundException;
import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.model.Vehicle;
import com.freightfox.dispatch.repository.OrderRepository;
import com.freightfox.dispatch.repository.VehicleRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private DispatchOptimizationService optimizationService;
    @InjectMocks
    private DispatchService dispatchService;

    private OrderRequest orderRequest(String id, int weight) {
        return new OrderRequest(id, 28.6139, 77.2090, "Connaught Place, Delhi, India",
                BigDecimal.valueOf(weight), OrderPriority.HIGH);
    }

    private VehicleRequest vehicleRequest(String id, int capacity) {
        return new VehicleRequest(id, BigDecimal.valueOf(capacity), 28.7041, 77.1025, "Karol Bagh, Delhi, India");
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveOrdersInsertsNewOrdersAndUpdatesExistingOnesInOneBatch() {
        DeliveryOrder existing = new DeliveryOrder("ORD001", 1, 1, "Old", BigDecimal.ONE, OrderPriority.LOW);
        when(orderRepository.findByOrderIdIn(List.of("ORD001", "ORD002"))).thenReturn(List.of(existing));

        dispatchService.saveOrders(List.of(orderRequest("ORD001", 15), orderRequest("ORD002", 20)));

        ArgumentCaptor<List<DeliveryOrder>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository).saveAll(captor.capture());
        List<DeliveryOrder> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertSame(existing, saved.get(0));
        assertEquals(BigDecimal.valueOf(15), existing.getPackageWeight());
        assertEquals(OrderPriority.HIGH, existing.getPriority());
        assertEquals("ORD002", saved.get(1).getOrderId());
    }

    @Test
    void saveOrdersRejectsDuplicateIdsWithinTheSameRequest() {
        InvalidDispatchRequestException exception = assertThrows(InvalidDispatchRequestException.class,
                () -> dispatchService.saveOrders(List.of(orderRequest("ORD001", 10), orderRequest("ORD001", 20))));

        assertEquals("Duplicate order IDs in request: ORD001", exception.getMessage());
        verify(orderRepository, never()).saveAll(any());
    }

    @Test
    void saveVehiclesRejectsDuplicateIdsWithinTheSameRequest() {
        assertThrows(InvalidDispatchRequestException.class,
                () -> dispatchService.saveVehicles(List.of(vehicleRequest("VEH001", 100), vehicleRequest("VEH001", 80))));
        verify(vehicleRepository, never()).saveAll(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void saveVehiclesUpdatesExistingVehicle() {
        Vehicle existing = new Vehicle("VEH001", BigDecimal.TEN, 1, 1, "Old");
        when(vehicleRepository.findByVehicleIdIn(List.of("VEH001"))).thenReturn(List.of(existing));

        dispatchService.saveVehicles(List.of(vehicleRequest("VEH001", 100)));

        ArgumentCaptor<List<Vehicle>> captor = ArgumentCaptor.forClass(List.class);
        verify(vehicleRepository).saveAll(captor.capture());
        assertSame(existing, captor.getValue().get(0));
        assertEquals(BigDecimal.valueOf(100), existing.getCapacity());
    }

    @Test
    void generatePlanFailsWhenThereAreNoOrders() {
        when(orderRepository.findAll()).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> dispatchService.generatePlan());
    }

    @Test
    void generatePlanFailsWhenThereAreNoVehicles() {
        when(orderRepository.findAll()).thenReturn(
                List.of(new DeliveryOrder("ORD001", 1, 1, "A", BigDecimal.ONE, OrderPriority.HIGH)));
        when(vehicleRepository.findAll()).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> dispatchService.generatePlan());
    }

    @Test
    void generatePlanDelegatesToTheOptimiser() {
        List<DeliveryOrder> orders = List.of(new DeliveryOrder("ORD001", 1, 1, "A", BigDecimal.ONE, OrderPriority.HIGH));
        List<Vehicle> vehicles = List.of(new Vehicle("VEH001", BigDecimal.TEN, 1, 1, "B"));
        DispatchPlanResponse expected = new DispatchPlanResponse(List.of(), List.of(), 0, 0, 0.0);
        when(orderRepository.findAll()).thenReturn(orders);
        when(vehicleRepository.findAll()).thenReturn(vehicles);
        when(optimizationService.optimize(orders, vehicles)).thenReturn(expected);

        assertSame(expected, dispatchService.generatePlan());
    }
}
