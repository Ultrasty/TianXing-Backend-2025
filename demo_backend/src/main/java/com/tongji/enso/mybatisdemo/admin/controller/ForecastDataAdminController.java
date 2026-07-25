package com.tongji.enso.mybatisdemo.admin.controller;

import com.tongji.enso.mybatisdemo.admin.dto.EcmwfImportRequest;
import com.tongji.enso.mybatisdemo.admin.dto.ForecastDataRequest;
import com.tongji.enso.mybatisdemo.admin.service.EcmwfImportService;
import com.tongji.enso.mybatisdemo.admin.service.ForecastDataService;
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

import java.util.LinkedHashMap;
import java.util.Map;

/** 功能 2.1-2.3：更新、删除、发布预报数据。所有接口都由 /admin/** 拦截器鉴权。 */
@RestController
@RequestMapping("/admin/forecast-data")
public class ForecastDataAdminController {
    private final ForecastDataService forecastDataService;
    private final EcmwfImportService ecmwfImportService;

    public ForecastDataAdminController(ForecastDataService forecastDataService,
                                       EcmwfImportService ecmwfImportService) {
        this.forecastDataService = forecastDataService;
        this.ecmwfImportService = ecmwfImportService;
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return forecastDataService.meta();
    }

    @GetMapping
    public Map<String, Object> page(@RequestParam String dataset,
                                    @RequestParam(required = false) String year,
                                    @RequestParam(required = false) String month,
                                    @RequestParam(required = false) String varModel,
                                    @RequestParam(required = false) Integer page,
                                    @RequestParam(required = false) Integer pageSize) {
        return forecastDataService.page(dataset, year, month, varModel, page, pageSize);
    }

    @GetMapping("/{id}")
    public Map<String, Object> findOne(@PathVariable long id, @RequestParam String dataset) {
        return forecastDataService.findOne(dataset, id);
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody ForecastDataRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(forecastDataService.create(request));
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @RequestBody ForecastDataRequest request) {
        return forecastDataService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id, @RequestParam String dataset) {
        forecastDataService.delete(dataset, id);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("message", "删除成功");
        result.put("id", id);
        return result;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> upload(@RequestParam String dataset,
                                                      @RequestParam String year,
                                                      @RequestParam String month,
                                                      @RequestParam String varModel,
                                                      @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(forecastDataService.upload(dataset, year, month, varModel, file));
    }

    @PostMapping("/ecmwf")
    public ResponseEntity<Map<String, Object>> importFromEcmwf(@RequestBody EcmwfImportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ecmwfImportService.importForecast(request));
    }
}
