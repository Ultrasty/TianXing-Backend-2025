package com.tongji.enso.mybatisdemo.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import com.tongji.enso.mybatisdemo.admin.evaluation.EcmwfPreviewRequest;
import com.tongji.enso.mybatisdemo.admin.evaluation.EcmwfPreviewService;
import com.tongji.enso.mybatisdemo.admin.evaluation.EvaluationValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EcmwfPreviewServiceTests {
    private final EcmwfPreviewService service = new EcmwfPreviewService(
            new ObjectMapper(), new EvaluationValidator());

    @Test
    void rejectsMissingRequestBeforeStartingPython() {
        assertThatThrownBy(() -> service.preview(null))
                .isInstanceOf(AdminException.class)
                .extracting("code")
                .isEqualTo("ECMWF_REQUEST_INVALID");
    }

    @Test
    void rejectsUnsafeParameterBeforeStartingPython() {
        EcmwfPreviewRequest request = validSieRequest();
        request.setParam("2t;Remove-Item");

        assertThatThrownBy(() -> service.preview(request))
                .isInstanceOf(AdminException.class)
                .extracting("code")
                .isEqualTo("ECMWF_REQUEST_INVALID");
    }

    @Test
    void validatesTargetEvaluationShapeBeforeStartingPython() {
        EcmwfPreviewRequest request = validSieRequest();
        request.setMonth(null);

        assertThatThrownBy(() -> service.preview(request))
                .isInstanceOf(AdminException.class)
                .extracting("code")
                .isEqualTo("EVALUATION_INVALID_DATA");
    }

    private EcmwfPreviewRequest validSieRequest() {
        EcmwfPreviewRequest request = new EcmwfPreviewRequest();
        request.setCategory("SIE");
        request.setYear("2026");
        request.setMonth("8");
        request.setVarModel("RMSD");
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
