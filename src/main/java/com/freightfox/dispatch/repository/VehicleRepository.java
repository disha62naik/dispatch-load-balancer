package com.freightfox.dispatch.repository;

import com.freightfox.dispatch.model.Vehicle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    List<Vehicle> findByVehicleIdIn(Collection<String> vehicleIds);
}
