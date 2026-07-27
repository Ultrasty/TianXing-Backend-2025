package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.tongji.enso.mybatisdemo.admin.common.AdminException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class EvaluationValidator {
    public static final Set<String> NAO_MODELS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "corr_lead1-6_ECMWF", "corr_lead1-6_ECCC", "corr_lead1-6_NAO-MCD")));
    public static final Set<String> SIC_FIXED_MODELS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "MITgcm(with DA)withBC_RMSE", "withDA_withoutBC_RMSE",
            "withoutDA_withBC_RMSE", "withoutDA_withoutBC")));
    public static final Set<String> SIE_MODELS = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "RMSD", "BAIS", "VAR", "CORRELATION", "OBS_STD", "PRE_STD")));

    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("^[0-9]{4}$");
    private static final Pattern SIC_YEAR_MODEL = Pattern.compile("^([0-9]{4})(?:_per)?_(?:BACC|RMSE)$");

    public void validateQuery(EvaluationCategory category, EvaluationQueryRequest query) {
        if (query.getPage() < 1) {
            invalid("page 必须大于等于 1");
        }
        if (query.getPageSize() < 1 || query.getPageSize() > 100) {
            invalid("pageSize 必须在 1 到 100 之间");
        }
        query.setYear(trimToNull(query.getYear()));
        query.setMonth(trimToNull(query.getMonth()));
        query.setDay(trimToNull(query.getDay()));
        query.setVarModel(trimToNull(query.getVarModel()));

        if (query.getYear() != null) {
            if (category == EvaluationCategory.NAO) {
                if (!"all".equalsIgnoreCase(query.getYear())) {
                    invalidDate("NAO相关系数评估记录的 year 只能是 all");
                }
                query.setYear("all");
            } else {
                query.setYear(normalizeYear(query.getYear()));
            }
        }
        if (query.getMonth() != null) {
            if (category == EvaluationCategory.NAO) {
                if (!"all".equalsIgnoreCase(query.getMonth())) {
                    invalidDate("NAO相关系数评估记录的 month 只能是 all");
                }
                query.setMonth("all");
            } else {
                query.setMonth(normalizeNumber(query.getMonth(), 1, 12, "month"));
            }
        }
        if (query.getDay() != null) {
            query.setDay(normalizeNumber(query.getDay(), 1, 31, "day"));
        }
        if (query.getVarModel() != null) {
            if (!(category == EvaluationCategory.SIC && query.getYear() == null &&
                    SIC_YEAR_MODEL.matcher(query.getVarModel()).matches())) {
                validateVarModel(category, query.getYear(), query.getVarModel());
            }
        }
        if (category == EvaluationCategory.ENSO &&
                (query.getMonth() != null || query.getDay() != null || query.getVarModel() != null)) {
            invalid("ENSO评估记录不包含 month、day 或 varModel");
        }
        if ((category == EvaluationCategory.NAO || category == EvaluationCategory.SIE) && query.getDay() != null) {
            invalid("该评估类别不包含 day");
        }
        if (category == EvaluationCategory.SIC && query.getDay() != null &&
                (query.getYear() == null || query.getMonth() == null)) {
            invalidDate("按 day 查询SIC时必须同时提供 year 和 month");
        }
        if (category == EvaluationCategory.SIC && query.getDay() != null) {
            validateDate(query.getYear(), query.getMonth(), query.getDay(), true);
        }
    }

    public void validateAndNormalize(EvaluationCategory category, EvaluationRecordRequest request) {
        if (request == null) {
            invalid("评估记录不能为空");
        }
        request.setYear(trimToNull(request.getYear()));
        request.setMonth(trimToNull(request.getMonth()));
        request.setDay(trimToNull(request.getDay()));
        request.setVarModel(trimToNull(request.getVarModel()));
        request.setSource(trimToNull(request.getSource()));

        switch (category) {
            case ENSO:
                request.setYear(normalizeYear(required(request.getYear(), "year")));
                forbidden(request.getMonth(), "month", category);
                forbidden(request.getDay(), "day", category);
                forbidden(request.getVarModel(), "varModel", category);
                break;
            case NAO:
                if (!"all".equalsIgnoreCase(required(request.getYear(), "year")) ||
                        !"all".equalsIgnoreCase(required(request.getMonth(), "month"))) {
                    invalidDate("NAO相关系数评估记录必须使用 year=all、month=all");
                }
                request.setYear("all");
                request.setMonth("all");
                forbidden(request.getDay(), "day", category);
                validateVarModel(category, request.getYear(), required(request.getVarModel(), "varModel"));
                break;
            case SIC:
                request.setYear(normalizeYear(required(request.getYear(), "year")));
                request.setMonth(normalizeNumber(required(request.getMonth(), "month"), 1, 12, "month"));
                request.setDay(normalizeNumber(required(request.getDay(), "day"), 1, 31, "day"));
                validateDate(request.getYear(), request.getMonth(), request.getDay(), true);
                validateVarModel(category, request.getYear(), required(request.getVarModel(), "varModel"));
                break;
            case SIE:
                request.setYear(normalizeYear(required(request.getYear(), "year")));
                request.setMonth(normalizeNumber(required(request.getMonth(), "month"), 1, 12, "month"));
                forbidden(request.getDay(), "day", category);
                validateVarModel(category, request.getYear(), required(request.getVarModel(), "varModel"));
                validateDate(request.getYear(), request.getMonth(), null, false);
                break;
            default:
                throw new IllegalStateException("Unsupported evaluation category");
        }

        validateData(request.getData());
        if (request.getSource() != null && request.getSource().length() > 32) {
            invalid("source 最长为 32 个字符");
        }
    }

    public String naturalKey(EvaluationCategory category, EvaluationRecordRequest request) {
        return category.name() + '|' + nullSafe(request.getYear()) + '|' + nullSafe(request.getMonth()) + '|' +
                nullSafe(request.getDay()) + '|' + nullSafe(request.getVarModel());
    }

    private void validateVarModel(EvaluationCategory category, String year, String varModel) {
        boolean allowed;
        switch (category) {
            case ENSO:
                allowed = varModel == null;
                break;
            case NAO:
                allowed = NAO_MODELS.contains(varModel);
                break;
            case SIC:
                Matcher matcher = SIC_YEAR_MODEL.matcher(varModel);
                allowed = SIC_FIXED_MODELS.contains(varModel) ||
                        (matcher.matches() && year != null && matcher.group(1).equals(year));
                break;
            case SIE:
                allowed = SIE_MODELS.contains(varModel);
                break;
            default:
                allowed = false;
        }
        if (!allowed) {
            throw new AdminException("EVALUATION_INVALID_VAR_MODEL", "varModel 不属于该类别的评估数据白名单",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private void validateData(JsonNode data) {
        if (data == null || data.isNull() || !data.isArray() || data.size() == 0) {
            throw new AdminException("EVALUATION_INVALID_DATA", "data 必须是非空JSON数组", HttpStatus.BAD_REQUEST);
        }
        validateNode(data);
    }

    private void validateNode(JsonNode node) {
        if (node == null || node.isNull()) {
            throw new AdminException("EVALUATION_INVALID_DATA", "data 不能包含 null", HttpStatus.BAD_REQUEST);
        }
        if (node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue())) {
            throw new AdminException("EVALUATION_INVALID_DATA", "data 不能包含 NaN 或 Infinity", HttpStatus.BAD_REQUEST);
        }
        if (node.isTextual()) {
            String text = node.textValue().trim();
            if (text.isEmpty() || "NaN".equalsIgnoreCase(text) || "Infinity".equalsIgnoreCase(text) ||
                    "+Infinity".equalsIgnoreCase(text) || "-Infinity".equalsIgnoreCase(text)) {
                throw new AdminException("EVALUATION_INVALID_DATA", "data 包含非法文本值", HttpStatus.BAD_REQUEST);
            }
        }
        if (node.isContainerNode()) {
            for (JsonNode child : node) {
                validateNode(child);
            }
        }
    }

    private String normalizeYear(String value) {
        if (!FOUR_DIGIT_YEAR.matcher(value).matches()) {
            invalidDate("year 必须是四位数字");
        }
        int year = Integer.parseInt(value);
        if (year < 1900 || year > 2100) {
            invalidDate("year 必须在 1900 到 2100 之间");
        }
        return String.valueOf(year);
    }

    private String normalizeNumber(String value, int min, int max, String field) {
        try {
            int number = Integer.parseInt(value);
            if (number < min || number > max) {
                invalidDate(field + " 超出有效范围");
            }
            return String.valueOf(number);
        } catch (NumberFormatException exception) {
            invalidDate(field + " 必须是数字");
            return null;
        }
    }

    private void validateDate(String yearValue, String monthValue, String dayValue, boolean dayRequired) {
        try {
            int year = Integer.parseInt(yearValue);
            int month = Integer.parseInt(monthValue);
            if (dayRequired) {
                LocalDate.of(year, month, Integer.parseInt(dayValue));
            } else {
                YearMonth.of(year, month);
            }
        } catch (DateTimeException | NumberFormatException exception) {
            invalidDate("year、month、day 不是有效日期");
        }
    }

    private void forbidden(String value, String field, EvaluationCategory category) {
        if (value != null) {
            invalid(category.name() + "评估记录不允许字段 " + field);
        }
    }

    private String required(String value, String field) {
        if (value == null) {
            invalid("缺少必填字段 " + field);
        }
        return value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private void invalid(String message) {
        throw new AdminException("EVALUATION_INVALID_DATA", message, HttpStatus.BAD_REQUEST);
    }

    private void invalidDate(String message) {
        throw new AdminException("EVALUATION_INVALID_DATE", message, HttpStatus.BAD_REQUEST);
    }
}
