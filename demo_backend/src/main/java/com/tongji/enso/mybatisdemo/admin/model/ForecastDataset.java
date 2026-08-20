package com.tongji.enso.mybatisdemo.admin.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 功能 2.1-2.3 可维护的“预报数据”集合。
 *
 * 注意：这里只纳入当前前台预测结果真正使用的数值型 var_model，
 * 避免与 2.7-2.9 的预测评估数据发生职责重叠。
 */
public enum ForecastDataset {
    ENSO("tj_enso", Arrays.asList(
            "nino34_asc",
            "nino34_gtc",
            "nino34_mc",
            "nino34_mean"
    )),
    NAO("tj_nao", Arrays.asList(
            "index_NAO_MCD",
            "grid_NAO_MCD"
    )),
    SIE("tj_sie", Arrays.asList(
            "prediction_IceTFT",
            "mean_IceTFT",
            "upper_IceTFT",
            "lower_IceTFT"
    ));

    private final String tableName;
    private final List<String> forecastModels;

    ForecastDataset(String tableName, List<String> forecastModels) {
        this.tableName = tableName;
        this.forecastModels = Collections.unmodifiableList(forecastModels);
    }

    public String getTableName() {
        return tableName;
    }

    public List<String> getForecastModels() {
        return forecastModels;
    }

    public boolean supportsModel(String varModel) {
        return varModel != null && forecastModels.contains(varModel);
    }

    public static ForecastDataset from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("dataset 不能为空");
        }
        for (ForecastDataset dataset : values()) {
            if (dataset.name().equalsIgnoreCase(value.trim())) {
                return dataset;
            }
        }
        throw new IllegalArgumentException("不支持的数据集: " + value + "，仅支持 ENSO / NAO / SIE");
    }
}
