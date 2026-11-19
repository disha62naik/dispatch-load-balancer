package com.freightfox.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record VehicleBatchRequest(
        @NotEmpty(message = "At least one vehicle is required.") List<@NotNull(message = "Vehicle entries must not be null.") @Valid VehicleRequest> vehicles) {
}
