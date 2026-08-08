package com.tongji.enso.mybatisdemo.controller;

import com.tongji.enso.mybatisdemo.entity.admin.AdminApiResponse;
import com.tongji.enso.mybatisdemo.service.admin.ForecastResultImagePublishService;
import com.tongji.enso.mybatisdemo.service.admin.ForecastResultImagePublishService.ImageTypeOption;
import com.tongji.enso.mybatisdemo.service.admin.ForecastResultImagePublishService.PublishedImage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/forecast-result-images")
public class AdminForecastResultImageController {
    @Autowired
    private ForecastResultImagePublishService publishService;

    @GetMapping("/types")
    public AdminApiResponse<List<ImageTypeOption>> listTypes() {
        return AdminApiResponse.ok("获取图片类型成功", publishService.listSupportedTypes());
    }

    @PostMapping(value = "/manual", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminApiResponse<Map<String, Object>> publishManual(
            @RequestParam String year,
            @RequestParam String month,
            @RequestParam(required = false) String day,
            @RequestParam String type,
            @RequestParam("files") MultipartFile[] files) {
        return AdminApiResponse.ok("预报结果图发布成功", toResponse(publishService.publishMultipart(year, month, day, type, files)));
    }

    @PostMapping("/ecmwf")
    public AdminApiResponse<Map<String, Object>> publishFromEcmwf(@RequestBody EcmwfPublishRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        PublishedImage publishedImage = publishService.publishFromEcmwfUrls(
                request.getYear(),
                request.getMonth(),
                request.getDay(),
                request.getType(),
                request.getImageUrls());
        return AdminApiResponse.ok("预报结果图发布成功", toResponse(publishedImage));
    }

    private Map<String, Object> toResponse(PublishedImage publishedImage) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", publishedImage.getId());
        response.put("year", publishedImage.getYear());
        response.put("month", publishedImage.getMonth());
        response.put("day", publishedImage.getDay());
        response.put("type", publishedImage.getType());
        response.put("paths", publishedImage.getPaths());
        response.put("verifyPath", publishedImage.getVerifyPath());
        return response;
    }

    public static class EcmwfPublishRequest {
        private String year;
        private String month;
        private String day;
        private String type;
        private List<String> imageUrls;

        public String getYear() {
            return year;
        }

        public void setYear(String year) {
            this.year = year;
        }

        public String getMonth() {
            return month;
        }

        public void setMonth(String month) {
            this.month = month;
        }

        public String getDay() {
            return day;
        }

        public void setDay(String day) {
            this.day = day;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getImageUrls() {
            return imageUrls;
        }

        public void setImageUrls(List<String> imageUrls) {
            this.imageUrls = imageUrls;
        }
    }
}
