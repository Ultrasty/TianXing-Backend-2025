package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

public enum EvaluationCategory {
    ENSO,
    NAO,
    SIC,
    SIE;

    public static EvaluationCategory parse(String value) {
        if (value != null) {
            try {
                return valueOf(value.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Converted to the stable API error below.
            }
        }
        throw new AdminException("EVALUATION_INVALID_CATEGORY", "评估类别必须是 ENSO、NAO、SIC 或 SIE",
                HttpStatus.BAD_REQUEST);
    }
}
