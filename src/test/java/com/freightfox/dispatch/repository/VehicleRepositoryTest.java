package com.freightfox.dispatch.repository;

import com.freightfox.dispatch.model.Vehicle;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class VehicleRepositoryTest {

    @Autowired
    private VehicleRepository vehicleRepository;
    @Autowired
    private TestEntityManager entityManager;

    private Vehicle vehicle(String id, int capacity) {
        return new Vehicle(id, BigDecimal.valueOf(capacity), 28.7041, 77.1025, "Karol Bagh, Delhi, India");
    }

    @Test
    void findByVehicleIdInReturnsOnlyMatchingVehicles() {
        vehicleRepository.saveAll(List.of(vehicle("VEH001", 100), vehicle("VEH002", 80)));
        vehicleRepository.flush();

        assertEquals(1, vehicleRepository.findByVehicleIdIn(List.of("VEH002", "MISSING")).size());
    }

    @Test
    void updatingAnExistingVehicleKeepsOneRow() {
        vehicleRepository.saveAndFlush(vehicle("VEH001", 100));
        entityManager.clear();

        Vehicle existing = vehicleRepository.findByVehicleIdIn(List.of("VEH001")).get(0);
        existing.update(BigDecimal.valueOf(150), 12.9716, 77.6413, "Indiranagar, Bangalore, Karnataka, India");
        vehicleRepository.saveAndFlush(existing);
        entityManager.clear();

        List<Vehicle> all = vehicleRepository.findAll();
        assertEquals(1, all.size());
        assertEquals(0, BigDecimal.valueOf(150).compareTo(all.get(0).getCapacity()));
    }

    @Test
    void duplicateBusinessIdIsRejectedByUniqueConstraint() {
        vehicleRepository.saveAndFlush(vehicle("VEH001", 100));

        assertThrows(DataIntegrityViolationException.class,
                () -> vehicleRepository.saveAndFlush(vehicle("VEH001", 50)));
    }
}
