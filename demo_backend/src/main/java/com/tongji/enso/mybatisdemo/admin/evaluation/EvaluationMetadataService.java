package com.tongji.enso.mybatisdemo.admin.evaluation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EvaluationMetadataService {
    private final int maxRecords;
    private final long maxFileSizeBytes;

    public EvaluationMetadataService(@Value("${admin.import.max-records:500}") int maxRecords,
                                     @Value("${admin.import.max-file-size-bytes:10485760}") long maxFileSizeBytes) {
        this.maxRecords = maxRecords;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public Map<String, Object> getMetadata() {
        Map<String, Object> categories = new LinkedHashMap<>();
        categories.put("ENSO", category("obs_enso", Arrays.asList("year", "data"),
                Collections.emptyList(), "year"));
        categories.put("NAO", category("tj_nao", Arrays.asList("year", "month", "varModel", "data"),
                Arrays.asList("corr_lead1-6_ECMWF", "corr_lead1-6_ECCC", "corr_lead1-6_NAO-MCD"),
                "year+month+varModel (year/month are all/all)"));
        categories.put("SIC", category("tj_sic", Arrays.asList("year", "month", "day", "varModel", "data"),
                Arrays.asList("{year}_BACC", "{year}_per_BACC", "{year}_RMSE", "{year}_per_RMSE",
                        "MITgcm(with DA)withBC_RMSE", "withDA_withoutBC_RMSE",
                        "withoutDA_withBC_RMSE", "withoutDA_withoutBC"),
                "year+month+day+varModel"));
        categories.put("SIE", category("tj_sie", Arrays.asList("year", "month", "varModel", "data"),
                Arrays.asList("RMSD", "BAIS", "VAR", "CORRELATION", "OBS_STD", "PRE_STD"),
                "year+month+varModel"));

        Map<String, Object> importMetadata = new LinkedHashMap<>();
        importMetadata.put("formats", Collections.singletonList("JSON"));
        importMetadata.put("modes", Arrays.asList("REJECT", "UPSERT"));
        importMetadata.put("maxRecords", maxRecords);
        importMetadata.put("maxFileSizeBytes", maxFileSizeBytes);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("categories", categories);
        result.put("import", importMetadata);
        return result;
    }

    private Map<String, Object> category(String table, Object requiredFields, Object allowedVarModels,
                                         String naturalKey) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("table", table);
        metadata.put("requiredFields", requiredFields);
        metadata.put("allowedVarModels", allowedVarModels);
        metadata.put("dataType", "non-empty JSON array");
        metadata.put("naturalKey", naturalKey);
        return metadata;
    }
}
