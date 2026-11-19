package com.freightfox.dispatch.model;

import com.freightfox.dispatch.enums.OrderPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_orders")
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "delivery_order_seq")
    @SequenceGenerator(name = "delivery_order_seq", sequenceName = "delivery_orders_seq", allocationSize = 50)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 50)
    private String orderId;

    @Column(name = "latitude", nullable = false)
    private double latitude;

    @Column(name = "longitude", nullable = false)
    private double longitude;

    @Column(name = "address", nullable = false, columnDefinition = "TEXT")
    private String address;

    @Column(name = "package_weight", nullable = false, precision = 12, scale = 2)
    private BigDecimal packageWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private OrderPriority priority;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected DeliveryOrder() {
    }

    public DeliveryOrder(String orderId, double latitude, double longitude, String address,
                         BigDecimal packageWeight, OrderPriority priority) {
        this.orderId = orderId;
        update(latitude, longitude, address, packageWeight, priority);
    }

    public void update(double latitude, double longitude, String address,
                       BigDecimal packageWeight, OrderPriority priority) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.packageWeight = packageWeight;
        this.priority = priority;
    }

    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getAddress() {
        return address;
    }

    public BigDecimal getPackageWeight() {
        return packageWeight;
    }

    public OrderPriority getPriority() {
        return priority;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
