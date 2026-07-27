package com.tongji.enso.mybatisdemo.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import com.tongji.enso.mybatisdemo.admin.evaluation.EvaluationCategory;
import com.tongji.enso.mybatisdemo.admin.evaluation.EvaluationRecordRequest;
import com.tongji.enso.mybatisdemo.admin.evaluation.EvaluationValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationValidatorTests {
    private final EvaluationValidator validator = new EvaluationValidator();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesAndValidatesRealCategoryShapes() throws Exception {
        EvaluationRecordRequest enso = record("2026", null, null, null, "[0.1]");
        validator.validateAndNormalize(EvaluationCategory.ENSO, enso);

        EvaluationRecordRequest nao = record("ALL", "ALL", null, "corr_lead1-6_ECMWF", "[0.2]");
        validator.validateAndNormalize(EvaluationCategory.NAO, nao);
        assertThat(nao.getYear()).isEqualTo("all");

        EvaluationRecordRequest sic = record("2024", "02", "29", "2024_RMSE", "[0.3]");
        validator.validateAndNormalize(EvaluationCategory.SIC, sic);
        assertThat(sic.getMonth()).isEqualTo("2");

        EvaluationRecordRequest sie = record("2025", "1", null, "RMSD", "[0.4]");
        validator.validateAndNormalize(EvaluationCategory.SIE, sie);
    }

    @Test
    void rejectsForecastAndUnknownModels() throws Exception {
        assertThatThrownBy(() -> validator.validateAndNormalize(EvaluationCategory.SIC,
                record("2026", "1", "1", "SIC_Ice-BCNet", "[1]")))
                .isInstanceOf(AdminException.class)
                .extracting("code").isEqualTo("EVALUATION_INVALID_VAR_MODEL");

        assertThatThrownBy(() -> validator.validateAndNormalize(EvaluationCategory.SIE,
                record("2026", "1", null, "prediction_IceTFT", "[1]")))
                .isInstanceOf(AdminException.class)
                .extracting("code").isEqualTo("EVALUATION_INVALID_VAR_MODEL");
    }

    @Test
    void rejectsImpossibleDatesAndInvalidData() throws Exception {
        assertThatThrownBy(() -> validator.validateAndNormalize(EvaluationCategory.SIC,
                record("2023", "2", "29", "2023_RMSE", "[1]")))
                .isInstanceOf(AdminException.class)
                .extracting("code").isEqualTo("EVALUATION_INVALID_DATE");

        assertThatThrownBy(() -> validator.validateAndNormalize(EvaluationCategory.SIE,
                record("2026", "1", null, "RMSD", "[]")))
                .isInstanceOf(AdminException.class)
                .extracting("code").isEqualTo("EVALUATION_INVALID_DATA");
    }

    private EvaluationRecordRequest record(String year, String month, String day, String model, String data)
            throws Exception {
        EvaluationRecordRequest request = new EvaluationRecordRequest();
        request.setYear(year);
        request.setMonth(month);
        request.setDay(day);
        request.setVarModel(model);
        request.setData(objectMapper.readTree(data));
        return request;
    }
}
