package com.freightfox.dispatch.dto;

import com.freightfox.dispatch.enums.OrderPriority;
import io.swagger.v3.oas.annotations.media.Schema;

public record UnassignedOrderResponse(
        @Schema(example = "ORD006") String orderId,
        @Schema(example = "Connaught Place, Delhi, India") String address,
        @Schema(example = "40") double packageWeight,
        @Schema(example = "LOW") OrderPriority priority,
        @Schema(example = "Insufficient remaining capacity on all vehicles.") String reason) {
}
