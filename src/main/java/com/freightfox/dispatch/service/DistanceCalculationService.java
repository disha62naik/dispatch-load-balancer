package com.freightfox.dispatch.service;

import com.freightfox.dispatch.model.DeliveryOrder;
import com.freightfox.dispatch.util.HaversineCalculator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DistanceCalculationService {

    public double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        return HaversineCalculator.distanceKm(lat1, lon1, lat2, lon2);
    }

    /** Distance from the start point through every stop in order. No return leg is added. */
    public double routeDistanceKm(double startLatitude, double startLongitude, List<DeliveryOrder> route) {
        double total = 0;
        double currentLat = startLatitude;
        double currentLon = startLongitude;
        for (DeliveryOrder stop : route) {
            total += distanceKm(currentLat, currentLon, stop.getLatitude(), stop.getLongitude());
            currentLat = stop.getLatitude();
            currentLon = stop.getLongitude();
        }
        return total;
    }
}
