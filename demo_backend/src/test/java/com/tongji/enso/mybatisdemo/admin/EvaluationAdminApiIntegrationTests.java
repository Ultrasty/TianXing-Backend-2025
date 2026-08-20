package com.tongji.enso.mybatisdemo.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EvaluationAdminApiIntegrationTests {
    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private com.tongji.enso.mybatisdemo.admin.evaluation.NsidcEvaluationProcess nsidcProcess;

    private String loginPassword;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM evaluation_metric_provenance");
        jdbcTemplate.update("DELETE FROM info_sic_latlon");
        jdbcTemplate.update("DELETE FROM admin_users");
        jdbcTemplate.update("DELETE FROM obs_enso");
        jdbcTemplate.update("DELETE FROM tj_nao");
        jdbcTemplate.update("DELETE FROM tj_sic");
        jdbcTemplate.update("DELETE FROM tj_sie");
        loginPassword = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO admin_users(username,password_hash,enabled) VALUES(?,?,?)",
                "integration-admin", new BCryptPasswordEncoder().encode(loginPassword), true);
        token = login("integration-admin", loginPassword);
    }

    @Test
    void requiresAuthenticationAndRejectsBadLogin() throws Exception {
        mockMvc.perform(get("/admin/evaluations/meta"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"integration-admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"));

        mockMvc.perform(post("/admin/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"missing-admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"));

        mockMvc.perform(get("/admin/evaluations/meta").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categories.SIC.allowedVarModels").isArray());

        mockMvc.perform(get("/admin/evaluations/meta").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized());

        jdbcTemplate.update("UPDATE admin_users SET enabled=? WHERE username=?", false, "integration-admin");
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", "integration-admin")
                                .put("password", loginPassword).toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"));

        mockMvc.perform(get("/admin/evaluations/meta").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void supportsCrudForAllFourManagedCategories() throws Exception {
        String[] requests = {
                "{\"category\":\"ENSO\",\"year\":\"2026\",\"data\":[0.1,0.2]}",
                "{\"category\":\"NAO\",\"year\":\"all\",\"month\":\"all\",\"varModel\":\"corr_lead1-6_ECMWF\",\"data\":[0.3]}",
                "{\"category\":\"SIC\",\"year\":\"2026\",\"month\":\"2\",\"day\":\"28\",\"varModel\":\"2026_RMSE\",\"data\":[0.4]}",
                "{\"category\":\"SIE\",\"year\":\"2026\",\"month\":\"2\",\"varModel\":\"RMSD\",\"data\":[0.5]}"
        };
        String[] categories = {"ENSO", "NAO", "SIC", "SIE"};

        for (int index = 0; index < categories.length; index++) {
            String response = mockMvc.perform(post("/admin/evaluations")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requests[index]))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.category").value(categories[index]))
                    .andReturn().getResponse().getContentAsString();
            long id = objectMapper.readTree(response).path("data").path("id").asLong();

            mockMvc.perform(get("/admin/evaluations")
                            .header("Authorization", "Bearer " + token)
                            .param("category", categories[index]))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.total").value(1));

            JsonNode update = objectMapper.readTree(requests[index]);
            ((com.fasterxml.jackson.databind.node.ObjectNode) update).remove("category");
            ((com.fasterxml.jackson.databind.node.ObjectNode) update).set("data", objectMapper.readTree("[8.8]"));
            mockMvc.perform(put("/admin/evaluations/{category}/{id}", categories[index], id)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(update.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.data[0]").value(8.8));

            mockMvc.perform(delete("/admin/evaluations/{category}/{id}", categories[index], id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
            mockMvc.perform(get("/admin/evaluations/{category}/{id}", categories[index], id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void cannotReadUpdateOrDeleteForecastRowsThroughEvaluationApi() throws Exception {
        jdbcTemplate.update("INSERT INTO tj_sic(year,month,day,var_model,data) VALUES(?,?,?,?,?)",
                "2026", "1", "1", "SIC_Ice-BCNet", "[1]");
        long sicId = jdbcTemplate.queryForObject("SELECT id FROM tj_sic WHERE var_model='SIC_Ice-BCNet'", Long.class);
        jdbcTemplate.update("INSERT INTO tj_sie(year,month,var_model,data) VALUES(?,?,?,?)",
                "2026", "1", "prediction_IceTFT", "[1]");
        long sieId = jdbcTemplate.queryForObject("SELECT id FROM tj_sie WHERE var_model='prediction_IceTFT'", Long.class);

        mockMvc.perform(get("/admin/evaluations/SIC/{id}", sicId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/admin/evaluations/SIE/{id}", sieId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/admin/evaluations/SIC/{id}", sicId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\":\"2026\",\"month\":\"1\",\"day\":\"1\",\"varModel\":\"2026_RMSE\",\"data\":[2]}"))
                .andExpect(status().isNotFound());

        assertThat(jdbcTemplate.queryForObject("SELECT var_model FROM tj_sic WHERE id=?", String.class, sicId))
                .isEqualTo("SIC_Ice-BCNet");
    }

    @Test
    void importsManualJsonAndUpsertsNormalizedEcmwfJson() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "enso.json", "application/json",
                "{\"category\":\"ENSO\",\"records\":[{\"year\":\"2028\",\"data\":[0.7]}]}"
                        .getBytes(StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/admin/evaluations/import/manual")
                        .file(file)
                        .param("category", "ENSO")
                        .param("mode", "REJECT")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inserted").value(1));

        jdbcTemplate.update("INSERT INTO tj_sie(year,month,var_model,data) VALUES(?,?,?,?)",
                "2026", "3", "RMSD", "[1]");
        String batch = "{\"source\":\"ECMWF\",\"dataKind\":\"EVALUATION_METRIC\",\"mode\":\"UPSERT\",\"category\":\"SIE\",\"records\":[" +
                "{\"year\":\"2026\",\"month\":\"3\",\"varModel\":\"RMSD\",\"data\":[2]}," +
                "{\"year\":\"2026\",\"month\":\"3\",\"varModel\":\"VAR\",\"data\":[3]}]}";
        mockMvc.perform(post("/admin/evaluations/import/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated").value(1))
                .andExpect(jsonPath("$.data.inserted").value(1));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT data FROM tj_sie WHERE year='2026' AND month='3' AND var_model='RMSD'", String.class))
                .isEqualTo("[2]");
    }

    @Test
    void rejectsNonMetricBatchPayload() throws Exception {
        String nonMetric = "{\"source\":\"ECMWF\",\"dataKind\":\"UNVERIFIED\"," +
                "\"mode\":\"UPSERT\",\"category\":\"SIE\",\"records\":[" +
                "{\"year\":\"2026\",\"month\":\"8\",\"varModel\":\"RMSD\",\"data\":[273.15]}]}";

        mockMvc.perform(post("/admin/evaluations/import/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(nonMetric))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_FILE_INVALID"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tj_sie WHERE year='2026' AND month='8'", Integer.class)).isZero();
    }

    @Test
    void rejectsDuplicatesAndRollsBackWholeBatchOnDatabaseFailure() throws Exception {
        jdbcTemplate.update("INSERT INTO obs_enso(year,data) VALUES(?,?)", "2029", "[1]");
        String reject = "{\"source\":\"ECMWF\",\"dataKind\":\"EVALUATION_METRIC\",\"mode\":\"REJECT\",\"category\":\"ENSO\",\"records\":[" +
                "{\"year\":\"2029\",\"data\":[2]},{\"year\":\"2030\",\"data\":[3]}]}";
        mockMvc.perform(post("/admin/evaluations/import/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reject))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVALUATION_DUPLICATE"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM obs_enso WHERE year='2030'", Integer.class))
                .isZero();

        String rollback = "{\"source\":\"ECMWF\",\"dataKind\":\"EVALUATION_METRIC\",\"mode\":\"UPSERT\",\"category\":\"SIC\",\"records\":[" +
                "{\"year\":\"2027\",\"month\":\"1\",\"day\":\"1\",\"varModel\":\"2027_RMSE\",\"data\":[1]}," +
                "{\"year\":\"2027\",\"month\":\"1\",\"day\":\"2\",\"varModel\":\"2027_BACC\",\"data\":[999]}]}";
        mockMvc.perform(post("/admin/evaluations/import/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rollback))
                .andExpect(status().isInternalServerError());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM tj_sic WHERE year='2027'", Integer.class))
                .isZero();
    }

    @Test
    void existingPublicEvaluationEndpointStillNeedsNoAdminToken() throws Exception {
        jdbcTemplate.update("INSERT INTO tj_sic(year,month,day,var_model,data) VALUES(?,?,?,?,?)",
                "2023", "1", "1", "2023_BACC", "[1,2]");
        mockMvc.perform(get("/seaice/error").param("year", "2023").param("month", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.2023_BACC[0]").value(1));
    }

    @Test
    void publishedSicMetricsAppearInPublicChartsUpdateInPlaceAndDisappearAfterDeletion() throws Exception {
        String year = "2030";
        String month = "2";
        String[] models = {year + "_BACC", year + "_per_BACC", year + "_RMSE", year + "_per_RMSE"};
        String[] days = {"10", "2"};
        double[][] values = {{0.9, 0.8, 0.7, 0.6}, {0.2, 0.3, 0.4, 0.5}};
        long[][] ids = new long[days.length][models.length];

        for (int dayIndex = 0; dayIndex < days.length; dayIndex++) {
            for (int modelIndex = 0; modelIndex < models.length; modelIndex++) {
                ids[dayIndex][modelIndex] = createSicEvaluation(
                        year, month, days[dayIndex], models[modelIndex], values[dayIndex][modelIndex]);
            }
        }

        mockMvc.perform(get("/seaice/error").param("year", year).param("month", month))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.2030_BACC[0]").value(0.2))
                .andExpect(jsonPath("$.2030_BACC[1]").value(0.9))
                .andExpect(jsonPath("$.2030_per_BACC[0]").value(0.3))
                .andExpect(jsonPath("$.2030_RMSE[0]").value(0.4))
                .andExpect(jsonPath("$.2030_per_RMSE[0]").value(0.5));
        mockMvc.perform(get("/seaice/initial/SICError"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableMonths[0].year").value(year))
                .andExpect(jsonPath("$.availableMonths[0].month").value(month));

        updateSicEvaluation(ids[1][0], year, month, "2", year + "_BACC", 0.25);
        mockMvc.perform(get("/seaice/error").param("year", year).param("month", month))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.2030_BACC[0]").value(0.25))
                .andExpect(jsonPath("$.2030_BACC[1]").value(0.9));

        for (long[] dayIds : ids) {
            for (long id : dayIds) {
                deleteEvaluation("SIC", id);
            }
        }
        mockMvc.perform(get("/seaice/initial/SICError"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableMonths").isEmpty());
        mockMvc.perform(get("/seaice/error").param("year", year).param("month", month))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void publishedSicBoxMetricsExposeOnlyCompleteYearsAndDisappearAfterDeletion() throws Exception {
        String year = "2031";
        String[] models = {
                "withoutDA_withoutBC",
                "withoutDA_withBC_RMSE",
                "withDA_withoutBC_RMSE",
                "MITgcm(with DA)withBC_RMSE"
        };
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < models.length; index++) {
            ids.add(createSicEvaluation(year, "1", "1", models[index], index + 1.0, index + 1.5,
                    index + 2.0, index + 2.5, index + 3.0, index + 3.5, index + 4.0));
        }

        mockMvc.perform(get("/seaice/errorBox").param("year", year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.withoutDA_withoutBC[0][0]").value(1.0))
                .andExpect(jsonPath("$.withoutDA_withBC_RMSE[0][0]").value(2.0))
                .andExpect(jsonPath("$.withDA_withoutBC_RMSE[0][0]").value(3.0))
                .andExpect(jsonPath("$['MITgcm(with DA)withBC_RMSE'][0][0]").value(4.0));
        mockMvc.perform(get("/seaice/initial/SICErrorBox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearList[0]").value(year));

        for (long id : ids) {
            deleteEvaluation("SIC", id);
        }
        mockMvc.perform(get("/seaice/initial/SICErrorBox"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearList").isEmpty());
        mockMvc.perform(get("/seaice/errorBox").param("year", year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void publishedSieMetricsAppearInPublicChartsUpdateInPlaceAndDisappearAfterDeletion() throws Exception {
        String year = "2032";
        String[] models = {"RMSD", "BAIS", "VAR", "CORRELATION", "OBS_STD", "PRE_STD"};
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < models.length; index++) {
            ids.add(createSieEvaluation(year, "1", models[index], index + 0.1, index + 0.2));
        }

        mockMvc.perform(get("/seaice/predictionExamination/errorAnalysis").param("year", year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RMSD[0]").value(0.1))
                .andExpect(jsonPath("$.BAIS[0]").value(1.1))
                .andExpect(jsonPath("$.VAR[0]").value(2.1))
                .andExpect(jsonPath("$.CORRELATION[0]").value(3.1))
                .andExpect(jsonPath("$.OBS_STD[0]").value(4.1))
                .andExpect(jsonPath("$.PRE_STD[0]").value(5.1));
        mockMvc.perform(get("/seaice/initial/SIEErrorAnalysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearList[0]").value(year));

        updateSieEvaluation(ids.get(0), year, "1", "RMSD", 0.99, 1.0);
        mockMvc.perform(get("/seaice/predictionExamination/errorAnalysis").param("year", year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RMSD[0]").value(0.99));

        for (long id : ids) {
            deleteEvaluation("SIE", id);
        }
        mockMvc.perform(get("/seaice/initial/SIEErrorAnalysis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearList").isEmpty());
        mockMvc.perform(get("/seaice/predictionExamination/errorAnalysis").param("year", year))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void computesAndPublishesTraceableNsidcSicMetrics() throws Exception {
        jdbcTemplate.update("INSERT INTO info_sic_latlon(id,lat,lon) VALUES(?,?,?)",
                1, "[[70,70],[71,71]]", "[[0,1],[0,1]]");
        jdbcTemplate.update("INSERT INTO tj_sic(year,month,day,var_model,data) VALUES(?,?,?,?,?)",
                "2023", "4", "22", "SIC_Ice-BCNet",
                "[[[0,0],[0,0]],[[0,0],[0,0]],[[0,0],[0,0]],[[0,0],[0,0]]," +
                        "[[0,0],[0,0]],[[0,0],[0,0]],[[0,0],[0,0]]]");
        JsonNode fakeResult = objectMapper.readTree("{" +
                "\"source\":\"NSIDC\",\"dataKind\":\"EVALUATION_METRIC\",\"category\":\"SIC\"," +
                "\"predictionModel\":\"SIC_Ice-BCNet\"," +
                "\"observation\":{\"datasetId\":\"G10005\",\"version\":\"2\",\"sha256\":{\"2023-04\":\"abc\"}}," +
                "\"matching\":{\"validDates\":[\"2023-04-22\"]}," +
                "\"metricDefinitions\":{\"RMSE\":\"test\",\"BACC\":\"test\"}," +
                "\"records\":[" +
                "{\"year\":\"2023\",\"month\":\"4\",\"day\":\"22\",\"varModel\":\"2023_RMSE\",\"data\":[0.061],\"source\":\"NSIDC\"}," +
                "{\"year\":\"2023\",\"month\":\"4\",\"day\":\"22\",\"varModel\":\"2023_BACC\",\"data\":[0.96],\"source\":\"NSIDC\"}]}");
        org.mockito.Mockito.when(nsidcProcess.execute(org.mockito.ArgumentMatchers.any(JsonNode.class)))
                .thenReturn(fakeResult);

        mockMvc.perform(post("/admin/evaluations/nsidc/evaluate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"SIC\",\"year\":\"2023\",\"month\":\"4\"," +
                                "\"day\":\"22\",\"leadStartOffsetDays\":0,\"mode\":\"UPSERT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.published").value(true))
                .andExpect(jsonPath("$.data.publication.inserted").value(2))
                .andExpect(jsonPath("$.data.observation.datasetId").value("G10005"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tj_sic WHERE year='2023' AND day='22' AND var_model LIKE '2023_%'",
                Integer.class)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluation_metric_provenance WHERE source='NSIDC'",
                Integer.class)).isEqualTo(2);
    }

    private String login(String username, String password) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("username", username)
                .put("password", password)
                .toString();
        String response = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private long createSicEvaluation(String year, String month, String day, String varModel, double... data)
            throws Exception {
        return createEvaluation(objectMapper.createObjectNode()
                .put("category", "SIC")
                .put("year", year)
                .put("month", month)
                .put("day", day)
                .put("varModel", varModel)
                .set("data", objectMapper.valueToTree(data)));
    }

    private long createSieEvaluation(String year, String month, String varModel, double... data) throws Exception {
        return createEvaluation(objectMapper.createObjectNode()
                .put("category", "SIE")
                .put("year", year)
                .put("month", month)
                .put("varModel", varModel)
                .set("data", objectMapper.valueToTree(data)));
    }

    private long createEvaluation(JsonNode payload) throws Exception {
        String response = mockMvc.perform(post("/admin/evaluations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asLong();
    }

    private void updateSicEvaluation(long id, String year, String month, String day, String varModel,
                                     double... data) throws Exception {
        mockMvc.perform(put("/admin/evaluations/SIC/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("year", year)
                                .put("month", month)
                                .put("day", day)
                                .put("varModel", varModel)
                                .set("data", objectMapper.valueToTree(data))
                                .toString()))
                .andExpect(status().isOk());
    }

    private void updateSieEvaluation(long id, String year, String month, String varModel, double... data)
            throws Exception {
        mockMvc.perform(put("/admin/evaluations/SIE/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("year", year)
                                .put("month", month)
                                .put("varModel", varModel)
                                .set("data", objectMapper.valueToTree(data))
                                .toString()))
                .andExpect(status().isOk());
    }

    private void deleteEvaluation(String category, long id) throws Exception {
        mockMvc.perform(delete("/admin/evaluations/{category}/{id}", category, id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
