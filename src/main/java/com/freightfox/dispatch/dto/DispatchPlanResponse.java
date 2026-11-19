package com.freightfox.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record DispatchPlanResponse(
        List<VehicleDispatchResponse> dispatchPlan,
        List<UnassignedOrderResponse> unassignedOrders,
        @Schema(example = "1") int totalAssignedOrders,
        @Schema(example = "0") int totalUnassignedOrders,
        @Schema(description = "Sum of all vehicle route distances in kilometres", example = "12.45") double totalTravelDistance) {
}
