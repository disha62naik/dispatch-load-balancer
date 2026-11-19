package com.freightfox.dispatch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OrderBatchRequest(
        @NotEmpty(message = "At least one order is required.") List<@NotNull(message = "Order entries must not be null.") @Valid OrderRequest> orders) {
}
