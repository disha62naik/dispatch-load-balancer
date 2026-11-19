package com.freightfox.dispatch.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record ErrorResponse(
        @Schema(example = "2026-11-18T10:00:00") LocalDateTime timestamp,
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "Package weight must be greater than zero.") String message) {
}
