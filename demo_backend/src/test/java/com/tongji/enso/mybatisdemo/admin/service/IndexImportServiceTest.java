package com.tongji.enso.mybatisdemo.admin.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexImportServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private void assertOk(String jsonArray) throws Exception {
        assertDoesNotThrow(() ->
                IndexImportService.validateIndexValues(objectMapper.readTree(jsonArray), "ENSO"));
    }

    private void assertRejected(String jsonArray) throws Exception {
        assertThrows(ResponseStatusException.class, () ->
                IndexImportService.validateIndexValues(objectMapper.readTree(jsonArray), "ENSO"));
    }

    @Test
    void normalAnomalySeriesAccepted() throws Exception {
        assertOk("[0.05, -0.12, 0.43, 1.77, -0.55]");
    }

    @Test
    void boundaryValuesAccepted() throws Exception {
        assertOk("[5.0, -5.0, 0.0]");
    }

    @Test
    void absoluteSstValuesRejected() throws Exception {
        assertRejected("[25.42, 25.44, 25.46]");
    }

    @Test
    void oversizedValueRejected() throws Exception {
        assertRejected("[0.1, 6.5]");
    }

    @Test
    void nonNumericElementRejected() throws Exception {
        assertRejected("[0.1, \"abc\"]");
    }

    @Test
    void nonFiniteValueRejected() {
        com.fasterxml.jackson.databind.node.ArrayNode arr =
                objectMapper.createArrayNode().add(0.1).add(Double.NaN);
        assertThrows(ResponseStatusException.class, () ->
                IndexImportService.validateIndexValues(arr, "ENSO"));
    }
}
