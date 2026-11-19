package com.freightfox.dispatch.dto;

import com.freightfox.dispatch.enums.OrderPriority;
import io.swagger.v3.oas.annotations.media.Schema;

public record AssignedOrderResponse(
        @Schema(description = "Position in the vehicle's route, starting at 1", example = "1") int sequence,
        @Schema(example = "ORD001") String orderId,
        @Schema(example = "28.6139") double latitude,
        @Schema(example = "77.2090") double longitude,
        @Schema(example = "Connaught Place, Delhi, India") String address,
        @Schema(example = "15") double packageWeight,
        @Schema(example = "HIGH") OrderPriority priority) {
}
