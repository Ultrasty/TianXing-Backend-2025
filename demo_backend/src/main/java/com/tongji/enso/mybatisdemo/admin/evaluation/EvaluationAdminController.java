package com.tongji.enso.mybatisdemo.admin.evaluation;

import com.tongji.enso.mybatisdemo.admin.common.AdminApiResponse;
import com.tongji.enso.mybatisdemo.admin.common.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/admin/evaluations")
public class EvaluationAdminController {
    private final EvaluationService evaluationService;
    private final EvaluationImportService importService;
    private final EvaluationMetadataService metadataService;
    private final NsidcEvaluationService nsidcEvaluationService;

    public EvaluationAdminController(EvaluationService evaluationService, EvaluationImportService importService,
                                     EvaluationMetadataService metadataService,
                                     NsidcEvaluationService nsidcEvaluationService) {
        this.evaluationService = evaluationService;
        this.importService = importService;
        this.metadataService = metadataService;
        this.nsidcEvaluationService = nsidcEvaluationService;
    }

    @GetMapping("/meta")
    public AdminApiResponse<Map<String, Object>> metadata() {
        return AdminApiResponse.success(metadataService.getMetadata());
    }

    @GetMapping
    public AdminApiResponse<PageResult<EvaluationRecordResponse>> list(
            @RequestParam String category,
            @RequestParam(required = false) String year,
            @RequestParam(required = false) String month,
            @RequestParam(required = false) String day,
            @RequestParam(required = false) String varModel,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        EvaluationQueryRequest query = new EvaluationQueryRequest();
        query.setYear(year);
        query.setMonth(month);
        query.setDay(day);
        query.setVarModel(varModel);
        query.setPage(page);
        query.setPageSize(pageSize);
        return AdminApiResponse.success(evaluationService.list(EvaluationCategory.parse(category), query));
    }

    @GetMapping("/{category}/{id}")
    public AdminApiResponse<EvaluationRecordResponse> get(@PathVariable String category, @PathVariable long id) {
        return AdminApiResponse.success(evaluationService.get(EvaluationCategory.parse(category), id));
    }

    @PostMapping
    public ResponseEntity<AdminApiResponse<EvaluationRecordResponse>> create(
            @RequestBody EvaluationCreateRequest request) {
        EvaluationRecordResponse created = evaluationService.create(
                EvaluationCategory.parse(request.getCategory()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AdminApiResponse.success(created));
    }

    @PutMapping("/{category}/{id}")
    public AdminApiResponse<EvaluationRecordResponse> update(@PathVariable String category, @PathVariable long id,
                                                             @RequestBody EvaluationRecordRequest request) {
        return AdminApiResponse.success(evaluationService.update(EvaluationCategory.parse(category), id, request));
    }

    @DeleteMapping("/{category}/{id}")
    public AdminApiResponse<EvaluationRecordResponse> delete(@PathVariable String category, @PathVariable long id) {
        return AdminApiResponse.success(evaluationService.delete(EvaluationCategory.parse(category), id));
    }

    @PostMapping(value = "/import/manual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminApiResponse<EvaluationBatchImportResult> manualImport(
            @RequestPart("file") MultipartFile file,
            @RequestParam String category,
            @RequestParam(defaultValue = "REJECT") String mode) {
        return AdminApiResponse.success(importService.importManual(file, category, mode));
    }

    @PostMapping(value = "/import/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminApiResponse<EvaluationBatchImportResult> batchImport(
            @RequestBody EvaluationBatchImportRequest request) {
        return AdminApiResponse.success(importService.importBatch(request));
    }

    @PostMapping(value = "/nsidc/evaluate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AdminApiResponse<com.fasterxml.jackson.databind.JsonNode> evaluateFromNsidc(
            @RequestBody NsidcEvaluationRequest request) {
        return AdminApiResponse.success(nsidcEvaluationService.evaluate(request));
    }
}
