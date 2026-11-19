package com.freightfox.dispatch.dto;

import com.freightfox.dispatch.enums.OrderPriority;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record OrderRequest(
        @Schema(example = "ORD001") @NotBlank(message = "Order ID is required.") @Size(max = 50, message = "Order ID must not exceed 50 characters.") String orderId,
        @Schema(example = "28.6139") @NotNull(message = "Latitude is required.") @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.") @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.") Double latitude,
        @Schema(example = "77.2090") @NotNull(message = "Longitude is required.") @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.") @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.") Double longitude,
        @Schema(example = "Connaught Place, Delhi, India") @NotBlank(message = "Address is required.") String address,
        @Schema(example = "15") @NotNull(message = "Package weight is required.") @DecimalMin(value = "0.0", inclusive = false, message = "Package weight must be greater than zero.") BigDecimal packageWeight,
        @Schema(example = "HIGH") @NotNull(message = "Priority is required (HIGH, MEDIUM or LOW).") OrderPriority priority) {
}
