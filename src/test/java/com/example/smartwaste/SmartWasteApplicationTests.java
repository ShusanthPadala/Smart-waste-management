package com.example.smartwaste;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** End-to-end API tests against the seeded demo district. The live simulation loop is off. */
@SpringBootTest(properties = "smartwaste.simulation.enabled=false")
@AutoConfigureMockMvc
class SmartWasteApplicationTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @BeforeEach
    void resetDemo() throws Exception {
        // Explicit city so tests that switch cities can't leak into each other.
        mvc.perform(post("/api/settings/demo-city").contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"visakhapatnam\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void visakhapatnamIsTheDefaultDistrict() throws Exception {
        mvc.perform(get("/api/settings"))
                .andExpect(jsonPath("$.cityName").value("Visakhapatnam"))
                .andExpect(jsonPath("$.demoCity").value("visakhapatnam"))
                .andExpect(jsonPath("$.depot.lat").value(17.7345))
                .andExpect(jsonPath("$.demoCities[*].key", hasItems("visakhapatnam", "hyderabad")));
        mvc.perform(get("/api/bins"))
                .andExpect(jsonPath("$[0].locationName").value("MVP Colony Sector 6"))
                .andExpect(jsonPath("$[0].siteProfile").value("RESIDENTIAL"));
        mvc.perform(get("/api/simulation")).andExpect(jsonPath("$.cityName").value("Visakhapatnam"));
    }

    @Test
    void switchingDemoCityReplacesDistrict() throws Exception {
        mvc.perform(post("/api/settings/demo-city").contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"hyderabad\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cityName").value("Hyderabad"))
                .andExpect(jsonPath("$.depot.lat").value(17.4401));
        mvc.perform(get("/api/bins"))
                .andExpect(jsonPath("$", hasSize(24)))
                .andExpect(jsonPath("$[0].locationName").value("Green Park Residency"));
        mvc.perform(get("/api/fleet")).andExpect(jsonPath("$[*].latitude", everyItem(is(17.4401))));

        mvc.perform(post("/api/settings/demo-city").contentType(MediaType.APPLICATION_JSON).content("{\"city\":\"atlantis\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clearedDistrictCanBeMappedByHand() throws Exception {
        mvc.perform(post("/api/settings/clear")).andExpect(status().isOk());
        mvc.perform(get("/api/bins")).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/fleet")).andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/settings")).andExpect(jsonPath("$.cityName").value("Visakhapatnam"));

        mvc.perform(post("/api/bins").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"binCode\":\"V1\",\"locationName\":\"Fish Market\",\"siteProfile\":\"MARKET\","
                                + "\"latitude\":17.70,\"longitude\":83.29,\"capacity\":2400,\"currentFillPercentage\":40}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.siteProfile").value("MARKET"))
                .andExpect(jsonPath("$.zone").value("Unzoned"));
    }

    @Test
    void depotCanBeMovedButNotWhileTrucksAreOut() throws Exception {
        String moved = "{\"cityName\":\"Vizag\",\"depotLatitude\":17.72,\"depotLongitude\":83.31}";
        mvc.perform(put("/api/settings/district").contentType(MediaType.APPLICATION_JSON).content(moved))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cityName").value("Vizag"))
                .andExpect(jsonPath("$.depot.lon").value(83.31));
        mvc.perform(get("/api/fleet")).andExpect(jsonPath("$[*].longitude", everyItem(is(83.31))));

        mvc.perform(put("/api/settings/district").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cityName\":\"\",\"depotLatitude\":99,\"depotLongitude\":83.31}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cityName").exists())
                .andExpect(jsonPath("$.fieldErrors.depotLatitude").exists());

        long routeId = json(mvc.perform(post("/api/routes/plan")).andReturn().getResponse().getContentAsString())
                .get("routes").get(0).get("id").asLong();
        mvc.perform(post("/api/routes/" + routeId + "/dispatch")).andExpect(status().isOk());
        mvc.perform(put("/api/settings/district").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cityName\":\"Vizag\",\"depotLatitude\":17.73,\"depotLongitude\":83.30}"))
                .andExpect(status().isConflict());
        // Renaming alone is fine while trucks are out.
        mvc.perform(put("/api/settings/district").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cityName\":\"Visakhapatnam\",\"depotLatitude\":17.72,\"depotLongitude\":83.31}"))
                .andExpect(status().isOk());
    }

    private JsonNode json(String body) throws Exception {
        return mapper.readTree(body);
    }

    @Test
    void demoDistrictIsSeededWithPredictions() throws Exception {
        mvc.perform(get("/api/bins"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(24)))
                .andExpect(jsonPath("$[0].binCode").value("B001"))
                .andExpect(jsonPath("$[0].predictedFillPercentage").isNumber());

        JsonNode summary = json(mvc.perform(get("/api/dashboard/summary")).andReturn().getResponse().getContentAsString());
        assertEquals(24, summary.get("totalBins").asInt());
        assertTrue(summary.get("needsPickup").asInt() >= 3, "demo should start with bins to collect");
        assertTrue(summary.get("openAlerts").asInt() >= 3, "urgent bins raise alerts");
        assertEquals(3, summary.get("vehiclesAvailable").asInt());
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        mvc.perform(post("/api/bins").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"binCode\":\"bad code!\",\"locationName\":\"\",\"latitude\":120,\"longitude\":78,\"capacity\":-1,\"currentFillPercentage\":150}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.binCode").exists())
                .andExpect(jsonPath("$.fieldErrors.locationName").exists())
                .andExpect(jsonPath("$.fieldErrors.latitude").exists())
                .andExpect(jsonPath("$.fieldErrors.capacity").exists())
                .andExpect(jsonPath("$.fieldErrors.currentFillPercentage").exists());
    }

    @Test
    void binCrudWithUniqueCodes() throws Exception {
        String body = "{\"binCode\":\"b100\",\"locationName\":\"New Plaza\",\"zone\":\"Test\",\"wasteType\":\"RECYCLABLE\","
                + "\"latitude\":17.45,\"longitude\":78.36,\"capacity\":660,\"currentFillPercentage\":93}";
        JsonNode created = json(mvc.perform(post("/api/bins").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.binCode").value("B100"))
                .andExpect(jsonPath("$.status").value("URGENT"))
                .andReturn().getResponse().getContentAsString());

        mvc.perform(post("/api/bins").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());

        long id = created.get("id").asLong();
        mvc.perform(put("/api/bins/" + id).contentType(MediaType.APPLICATION_JSON).content(body.replace("93", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NORMAL"));

        mvc.perform(delete("/api/bins/" + id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/bins/" + id)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(containsString("not found")));
    }

    @Test
    void planDispatchAndCompleteEmptiesBins() throws Exception {
        JsonNode plan = json(mvc.perform(post("/api/routes/plan"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode routes = plan.get("routes");
        assertTrue(routes.size() >= 1);

        JsonNode route = routes.get(0);
        long routeId = route.get("id").asLong();
        assertEquals("PLANNED", route.get("status").asText());
        assertTrue(route.get("totalLoadLitres").asDouble() <= route.get("vehicleCapacityLitres").asDouble());
        long binId = route.get("stops").get(0).get("binId").asLong();

        mvc.perform(post("/api/routes/" + routeId + "/dispatch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(post("/api/routes/" + routeId + "/dispatch")).andExpect(status().isConflict());

        mvc.perform(post("/api/routes/" + routeId + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.stops[*].collected", everyItem(is(true))));

        mvc.perform(get("/api/bins/" + binId))
                .andExpect(jsonPath("$.currentFillPercentage", lessThan(5.0)))
                .andExpect(jsonPath("$.status").value("NORMAL"));

        mvc.perform(get("/api/metrics/lifetime"))
                .andExpect(jsonPath("$.routesCompleted").value(1))
                .andExpect(jsonPath("$.averageFillAtPickup", greaterThan(75.0)));
    }

    @Test
    void sustainabilityComparisonShowsSavings() throws Exception {
        mvc.perform(get("/api/sustainability/compare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseline.binsCollected").value(24))
                .andExpect(jsonPath("$.co2SavedPct", greaterThan(0.0)))
                .andExpect(jsonPath("$.treesEquivalent", greaterThan(0.0)));
    }

    @Test
    void forecastProjectsTwentyFourHours() throws Exception {
        long id = json(mvc.perform(get("/api/bins")).andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        mvc.perform(get("/api/bins/" + id + "/forecast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history", not(empty())))
                .andExpect(jsonPath("$.forecast", hasSize(25)));
    }

    @Test
    void csvImportAcceptsCodesAndRejectsUnknownBins() throws Exception {
        MockMultipartFile ok = new MockMultipartFile("file", "r.csv", "text/csv",
                "timestamp,bin,fillPercentage\n2099-01-01T08:00:00,B001,40\n2099-01-01T09:00:00,B001,44\n".getBytes());
        mvc.perform(multipart("/api/simulation/import").file(ok))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(2));

        MockMultipartFile bad = new MockMultipartFile("file", "r.csv", "text/csv", "2099-01-01T08:00:00,NOPE,40\n".getBytes());
        mvc.perform(multipart("/api/simulation/import").file(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("unknown bin")));
    }

    @Test
    void simulationSpeedIsValidated() throws Exception {
        mvc.perform(post("/api/simulation").contentType(MediaType.APPLICATION_JSON).content("{\"speed\":360,\"autopilot\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speed").value(360.0))
                .andExpect(jsonPath("$.autopilot").value(true));
        mvc.perform(post("/api/simulation").contentType(MediaType.APPLICATION_JSON).content("{\"speed\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/simulation").contentType(MediaType.APPLICATION_JSON).content("{\"speed\":60,\"autopilot\":false}"));
    }
}
