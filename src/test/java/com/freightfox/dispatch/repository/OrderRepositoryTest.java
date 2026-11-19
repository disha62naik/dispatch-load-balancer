package com.freightfox.dispatch.repository;

import com.freightfox.dispatch.enums.OrderPriority;
import com.freightfox.dispatch.model.DeliveryOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private TestEntityManager entityManager;

    private DeliveryOrder order(String id, int weight, OrderPriority priority) {
        return new DeliveryOrder(id, 28.6139, 77.2090, "Connaught Place, Delhi, India",
                BigDecimal.valueOf(weight), priority);
    }

    @Test
    void savedOrderGetsIdAndCreationTimestamp() {
        DeliveryOrder saved = orderRepository.saveAndFlush(order("ORD001", 15, OrderPriority.HIGH));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void findByOrderIdInReturnsOnlyMatchingOrders() {
        orderRepository.saveAll(List.of(order("ORD001", 10, OrderPriority.HIGH),
                order("ORD002", 20, OrderPriority.LOW), order("ORD003", 30, OrderPriority.MEDIUM)));
        orderRepository.flush();

        List<DeliveryOrder> found = orderRepository.findByOrderIdIn(List.of("ORD001", "ORD003", "MISSING"));

        assertEquals(2, found.size());
    }

    @Test
    void updatingAnExistingOrderKeepsOneRowAndChangesValues() {
        DeliveryOrder saved = orderRepository.saveAndFlush(order("ORD001", 15, OrderPriority.LOW));
        entityManager.clear();

        DeliveryOrder existing = orderRepository.findByOrderIdIn(List.of("ORD001")).get(0);
        existing.update(12.9716, 77.5946, "MG Road, Bangalore, Karnataka, India", BigDecimal.valueOf(25), OrderPriority.HIGH);
        orderRepository.saveAndFlush(existing);
        entityManager.clear();

        List<DeliveryOrder> all = orderRepository.findAll();
        assertEquals(1, all.size());
        assertEquals(saved.getId(), all.get(0).getId());
        assertEquals(OrderPriority.HIGH, all.get(0).getPriority());
        assertEquals(0, BigDecimal.valueOf(25).compareTo(all.get(0).getPackageWeight()));
        assertEquals("MG Road, Bangalore, Karnataka, India", all.get(0).getAddress());
    }

    @Test
    void duplicateBusinessIdIsRejectedByUniqueConstraint() {
        orderRepository.saveAndFlush(order("ORD001", 15, OrderPriority.HIGH));

        assertThrows(DataIntegrityViolationException.class,
                () -> orderRepository.saveAndFlush(order("ORD001", 20, OrderPriority.LOW)));
    }
}
