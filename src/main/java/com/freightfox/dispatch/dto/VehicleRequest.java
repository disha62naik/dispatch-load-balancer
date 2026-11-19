package com.freightfox.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record VehicleRequest(
        @Schema(example = "VEH001") @NotBlank(message = "Vehicle ID is required.") @Size(max = 50, message = "Vehicle ID must not exceed 50 characters.") String vehicleId,
        @Schema(example = "100") @NotNull(message = "Capacity is required.") @DecimalMin(value = "0.0", inclusive = false, message = "Capacity must be greater than zero.") BigDecimal capacity,
        @Schema(example = "28.7041") @NotNull(message = "Current latitude is required.") @DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90.") @DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90.") Double currentLatitude,
        @Schema(example = "77.1025") @NotNull(message = "Current longitude is required.") @DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180.") @DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180.") Double currentLongitude,
        @Schema(example = "Karol Bagh, Delhi, India") @NotBlank(message = "Current address is required.") String currentAddress) {
}
