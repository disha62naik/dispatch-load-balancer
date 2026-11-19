package com.freightfox.dispatch.repository;

import com.freightfox.dispatch.model.DeliveryOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface OrderRepository extends JpaRepository<DeliveryOrder, Long> {

    List<DeliveryOrder> findByOrderIdIn(Collection<String> orderIds);
}
