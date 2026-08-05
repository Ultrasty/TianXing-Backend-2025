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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
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

    private String loginPassword;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM admin_user");
        jdbcTemplate.update("DELETE FROM obs_enso");
        jdbcTemplate.update("DELETE FROM tj_nao");
        jdbcTemplate.update("DELETE FROM tj_sic");
        jdbcTemplate.update("DELETE FROM tj_sie");
        loginPassword = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO admin_user(username,password_hash,enabled) VALUES(?,?,?)",
                "integration-admin", new BCryptPasswordEncoder().encode(loginPassword), true);
        token = login("integration-admin", loginPassword);
    }

    @Test
    void requiresAuthenticationAndRejectsBadLogin() throws Exception {
        mockMvc.perform(get("/admin/evaluations/meta"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));

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
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));

        jdbcTemplate.update("UPDATE admin_user SET enabled=? WHERE username=?", false, "integration-admin");
        mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("username", "integration-admin")
                                .put("password", loginPassword).toString()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_LOGIN_FAILED"));

        mockMvc.perform(get("/admin/evaluations/meta").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
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
    void rejectsRawEcmwfFieldReductionAsEvaluationMetric() throws Exception {
        String rawField = "{\"source\":\"ECMWF\",\"dataKind\":\"RAW_FIELD_REDUCTION\"," +
                "\"mode\":\"UPSERT\",\"category\":\"SIE\",\"records\":[" +
                "{\"year\":\"2026\",\"month\":\"8\",\"varModel\":\"RMSD\",\"data\":[273.15]}]}";

        mockMvc.perform(post("/admin/evaluations/import/batch")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawField))
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
}
