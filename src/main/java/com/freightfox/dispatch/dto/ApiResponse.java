package com.freightfox.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiResponse(
        @Schema(example = "Delivery orders accepted.") String message,
        @Schema(example = "success") String status) {

    public static ApiResponse success(String message) {
        return new ApiResponse(message, "success");
    }
}
