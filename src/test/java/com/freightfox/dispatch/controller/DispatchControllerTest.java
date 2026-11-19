package com.freightfox.dispatch.controller;

import com.freightfox.dispatch.dto.AssignedOrderResponse;
import com.freightfox.dispatch.dto.DispatchPlanResponse;
import com.freightfox.dispatch.dto.VehicleDispatchResponse;
import com.freightfox.dispatch.enums.OrderPriority;
import com.freightfox.dispatch.exception.InvalidDispatchRequestException;
import com.freightfox.dispatch.exception.ResourceNotFoundException;
import com.freightfox.dispatch.service.DispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DispatchController.class)
class DispatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DispatchService dispatchService;

    private static String orderJson(String field, String value) {
        String latitude = field.equals("latitude") ? value : "28.6139";
        String weight = field.equals("packageWeight") ? value : "15";
        String priority = field.equals("priority") ? value : "\"HIGH\"";
        return """
                {"orders": [{"orderId": "ORD001", "latitude": %s, "longitude": 77.2090,
                 "address": "Connaught Place, Delhi, India", "packageWeight": %s, "priority": %s}]}
                """.formatted(latitude, weight, priority);
    }

    private ResultActions postOrders(String body) throws Exception {
        return mockMvc.perform(post("/api/dispatch/orders").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void validOrdersAreAcceptedWith201() throws Exception {
        postOrders(orderJson("none", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Delivery orders accepted."))
                .andExpect(jsonPath("$.status").value("success"));
        verify(dispatchService).saveOrders(any());
    }

    @Test
    void validVehiclesAreAcceptedWith201() throws Exception {
        String body = """
                {"vehicles": [{"vehicleId": "VEH001", "capacity": 100, "currentLatitude": 28.7041,
                 "currentLongitude": 77.1025, "currentAddress": "Karol Bagh, Delhi, India"}]}
                """;

        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Vehicle details accepted."))
                .andExpect(jsonPath("$.status").value("success"));
    }

    @Test
    void invalidPriorityReturns400WithAllowedValues() throws Exception {
        postOrders(orderJson("priority", "\"URGENT\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message", containsString("HIGH, MEDIUM, LOW")));
    }

    @Test
    void outOfRangeLatitudeReturns400() throws Exception {
        postOrders(orderJson("latitude", "95"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Latitude must be between -90 and 90.")));
    }

    @Test
    void zeroPackageWeightReturns400() throws Exception {
        postOrders(orderJson("packageWeight", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Package weight must be greater than zero.")));
    }

    @Test
    void negativePackageWeightReturns400() throws Exception {
        postOrders(orderJson("packageWeight", "-5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emptyOrdersArrayReturns400() throws Exception {
        postOrders("{\"orders\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("At least one order is required.")));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        postOrders("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void vehicleWithNegativeCapacityReturns400() throws Exception {
        String body = """
                {"vehicles": [{"vehicleId": "VEH001", "capacity": -1, "currentLatitude": 28.7041,
                 "currentLongitude": 77.1025, "currentAddress": "Karol Bagh, Delhi, India"}]}
                """;

        mockMvc.perform(post("/api/dispatch/vehicles").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Capacity must be greater than zero.")));
    }

    @Test
    void duplicateIdsFromTheServiceReturn400() throws Exception {
        doThrow(new InvalidDispatchRequestException("Duplicate order IDs in request: ORD001"))
                .when(dispatchService).saveOrders(any());

        postOrders(orderJson("none", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Duplicate order IDs in request: ORD001"));
    }

    @Test
    void dispatchPlanIsReturnedWith200() throws Exception {
        AssignedOrderResponse stop = new AssignedOrderResponse(1, "ORD001", 28.6139, 77.2090,
                "Connaught Place, Delhi, India", 15, OrderPriority.HIGH);
        VehicleDispatchResponse vehicle = new VehicleDispatchResponse("VEH001", 28.7041, 77.1025,
                "Karol Bagh, Delhi, India", 15, 12.45, List.of(stop));
        when(dispatchService.generatePlan())
                .thenReturn(new DispatchPlanResponse(List.of(vehicle), List.of(), 1, 0, 12.45));

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchPlan[0].vehicleId").value("VEH001"))
                .andExpect(jsonPath("$.dispatchPlan[0].totalDistance").value(12.45))
                .andExpect(jsonPath("$.dispatchPlan[0].assignedOrders[0].address").value("Connaught Place, Delhi, India"))
                .andExpect(jsonPath("$.unassignedOrders").isEmpty())
                .andExpect(jsonPath("$.totalAssignedOrders").value(1))
                .andExpect(jsonPath("$.totalTravelDistance").value(12.45));
    }

    @Test
    void planWithoutStoredDataReturns404() throws Exception {
        when(dispatchService.generatePlan()).thenThrow(new ResourceNotFoundException("No vehicles found."));

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedFailureReturns500WithoutLeakingDetails() throws Exception {
        when(dispatchService.generatePlan()).thenThrow(new IllegalStateException("secret internal detail"));

        mockMvc.perform(get("/api/dispatch/plan"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."));
    }
}
