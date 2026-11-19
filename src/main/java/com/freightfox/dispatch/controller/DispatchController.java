package com.freightfox.dispatch.controller;

import com.freightfox.dispatch.dto.ApiResponse;
import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.OrderBatchRequest;
import com.freightfox.dispatch.dto.VehicleBatchRequest;
import com.freightfox.dispatch.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dispatch")
@Tag(name = "Dispatch", description = "Submit orders and vehicles, then generate a dispatch plan")
public class DispatchController {

    private final DispatchService dispatchService;

    public DispatchController(DispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Accept delivery orders",
            description = "Inserts new orders and updates existing ones with the same orderId. "
                    + "Duplicate orderIds inside one request are rejected. Priority must be HIGH, MEDIUM or LOW.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Orders accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or duplicate order IDs in the request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Database unavailable")})
    public ApiResponse acceptOrders(@Valid @RequestBody OrderBatchRequest request) {
        dispatchService.saveOrders(request.orders());
        return ApiResponse.success("Delivery orders accepted.");
    }

    @PostMapping("/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Accept vehicle details",
            description = "Inserts new vehicles and updates existing ones with the same vehicleId. "
                    + "Capacity must be greater than zero.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Vehicles accepted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error or duplicate vehicle IDs in the request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Database unavailable")})
    public ApiResponse acceptVehicles(@Valid @RequestBody VehicleBatchRequest request) {
        dispatchService.saveVehicles(request.vehicles());
        return ApiResponse.success("Vehicle details accepted.");
    }

    @GetMapping("/plan")
    @Operation(summary = "Generate dispatch plan",
            description = "Builds a plan from the stored orders and vehicles. Distances are in kilometres (Haversine), "
                    + "rounded to two decimals. Orders that cannot be placed are listed under unassignedOrders.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dispatch plan generated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No orders or no vehicles stored yet"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Database unavailable")})
    public DispatchPlanResponse getDispatchPlan() {
        return dispatchService.generatePlan();
    }
}
