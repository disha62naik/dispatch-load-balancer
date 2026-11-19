package com.freightfox.dispatch.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HaversineCalculatorTest {

    @Test
    void sameCoordinatesReturnZero() {
        assertEquals(0.0, HaversineCalculator.distanceKm(28.6139, 77.2090, 28.6139, 77.2090), 1e-9);
    }

    @Test
    void differentCoordinatesReturnPositiveDistance() {
        assertTrue(HaversineCalculator.distanceKm(28.6139, 77.2090, 28.7041, 77.1025) > 0);
    }

    @Test
    void oneDegreeOfLatitudeIsAbout111Km() {
        assertEquals(111.19, HaversineCalculator.distanceKm(0, 0, 1, 0), 0.01);
    }

    @Test
    void matchesKnownDistanceBetweenLondonAndParis() {
        double distance = HaversineCalculator.distanceKm(51.5074, -0.1278, 48.8566, 2.3522);
        assertEquals(343.6, distance, 2.0);
    }

    @Test
    void distanceIsSymmetric() {
        double forward = HaversineCalculator.distanceKm(28.6139, 77.2090, 28.4595, 77.0266);
        double backward = HaversineCalculator.distanceKm(28.4595, 77.0266, 28.6139, 77.2090);
        assertEquals(forward, backward, 1e-9);
    }

    @Test
    void poleToPoleIsHalfTheEarthCircumference() {
        assertEquals(20015.09, HaversineCalculator.distanceKm(90, 0, -90, 0), 0.01);
    }

    @Test
    void antimeridianBoundaryReturnsZeroForSamePoint() {
        assertEquals(0.0, HaversineCalculator.distanceKm(0, -180, 0, 180), 1e-6);
    }
}
