package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EcmwfPreviewServiceTests {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EcmwfPreviewService service = new EcmwfPreviewService(objectMapper);

    @Test
    void rejectsMissingRequestBeforeStartingPython() {
        assertThatThrownBy(() -> service.preview(null))
                .isInstanceOf(AdminException.class)
                .extracting("code")
                .isEqualTo("ECMWF_REQUEST_INVALID");
    }

    @Test
    void rejectsUnsafeParameterBeforeStartingPython() {
        EcmwfPreviewRequest request = validRequest();
        request.setParam("2t;Remove-Item");

        assertThatThrownBy(() -> service.preview(request))
                .isInstanceOf(AdminException.class)
                .extracting("code")
                .isEqualTo("ECMWF_REQUEST_INVALID");
    }

    @Test
    void rejectsUnsupportedReducerBeforeStartingPython() {
        EcmwfPreviewRequest request = validRequest();
        request.setReducer("RMSD");

        assertThatThrownBy(() -> service.preview(request))
                .isInstanceOf(AdminException.class)
                .extracting("code")
                .isEqualTo("ECMWF_REQUEST_INVALID");
    }

    @Test
    void marksReducedFieldAsNotPublishable() throws Exception {
        JsonNode scriptOutput = objectMapper.readTree(
                "{\"data\":[281.13],\"metadata\":{\"units\":\"K\"}}");

        Map<String, Object> preview = service.toRawFieldPreview(scriptOutput);

        assertThat(preview.get("dataKind")).isEqualTo("RAW_FIELD_REDUCTION");
        assertThat(preview.get("publishable")).isEqualTo(false);
        assertThat(preview.get("values")).isEqualTo(scriptOutput.get("data"));
    }

    private EcmwfPreviewRequest validRequest() {
        EcmwfPreviewRequest request = new EcmwfPreviewRequest();
        request.setParam("2t");
        request.setTime(0);
        request.setStep(24);
        request.setProvider("ecmwf");
        request.setModel("ifs");
        request.setForecastType("fc");
        request.setReducer("MEAN");
        return request;
    }
}
