package com.freightfox.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record VehicleDispatchResponse(
        @Schema(example = "VEH001") String vehicleId,
        @Schema(example = "28.7041") double startLatitude,
        @Schema(example = "77.1025") double startLongitude,
        @Schema(example = "Karol Bagh, Delhi, India") String startAddress,
        @Schema(description = "Sum of assigned package weights", example = "45") double totalLoad,
        @Schema(description = "Route distance in kilometres, rounded to two decimals", example = "12.45") double totalDistance,
        @Schema(description = "Assigned orders in delivery sequence") List<AssignedOrderResponse> assignedOrders) {
}
