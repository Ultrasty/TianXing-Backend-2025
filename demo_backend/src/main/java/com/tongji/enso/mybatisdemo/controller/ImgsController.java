package com.tongji.enso.mybatisdemo.controller;

import com.tongji.enso.mybatisdemo.entity.online.Imgs;
import com.tongji.enso.mybatisdemo.mapper.online.ImgsMapper;
import com.tongji.enso.mybatisdemo.mapper.online.ImgsMapperEnso;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/imgs")
public class ImgsController {

    @Autowired
    private ImgsMapper imgsMapper;

    /**
     * 获取指定 年、月、日 的图片路径 WEA_MSLP
     * eg. http://localhost:8080/imgs/WEA_MSLP/getImgsPath?year=2019&month=1&day=1
     * @param year
     * @param month
     * @param day
     * @return
     */
    @GetMapping("/WEA_MSLP/getImgsPath")
    @ApiOperation(notes = "获取指定 年、月、日 的图片路径 WEA_MSLP", value = "获取指定 年、月、日 的图片路径 WEA_MSLP")
    public Map<String, Object> getMSLPImgsPathByDay(String year, String month, String day) {
        List<Imgs> imgsData = imgsMapper.findImgsInfoByDayType(year, month, day, "WEA_MSLP");

        // 将 data 字段从 JSON 字符串转换为 List<String>
        List<String> imgPaths = new ArrayList<>();
        if (!imgsData.isEmpty()) {
            String imgSrcData = imgsData.get(0).getData();
            imgPaths = Arrays.asList(imgSrcData.split(","));
        }

        Map<String, Object> result = new HashMap<>();

        // 从 0 时开始，每次 + 6
        int hour = 0;
        int dayIncrement = 0; // 用于处理跨天情况
        int daysInMonth = 30; // 假设每月固定为30天
        List<String> titlesList = new ArrayList<>();
        for (int i = 0; i < imgPaths.size(); i++)
        {
            if (hour >= 24) {
                hour -= 24;
                dayIncrement += 1;
            }

            int newDay = Integer.parseInt(day) + dayIncrement;
            int newMonth = Integer.parseInt(month);
            if (newDay > daysInMonth) {
                newDay -= daysInMonth;
                newMonth++;
            }

            titlesList.add(String.format("%s年%s月%s日%d时待定待定", year, newMonth, newDay, hour));
            hour += 6;
        }

        List<String> testsList = new ArrayList<>();
        for (int i = 0; i < imgPaths.size(); i++)
        {
            testsList.add("待定待定");
        }

        result.put("titles", titlesList);
        result.put("imgSrc", imgPaths);
        result.put("texts", testsList);

        return result;
    }

    /**
     * 获取指定 年、月、日 的图片路径 WEA_T2M
     * eg. http://localhost:8080/imgs/WEA_T2M/getImgsPath?year=2019&month=1&day=1
     * @param year
     * @param month
     * @param day
     * @return
     */
    @GetMapping("/WEA_T2M/getImgsPath")
    @ApiOperation(notes = "获取指定 年、月、日 的图片路径 WEA_T2M", value = "获取指定 年、月、日 的图片路径 WEA_T2M")
    public Map<String, Object> getT2MImgsPathByDay(String year, String month, String day) {
        List<Imgs> imgsData = imgsMapper.findImgsInfoByDayType(year, month, day, "WEA_T2M");

        // 将 data 字段从 JSON 字符串转换为 List<String>
        List<String> imgPaths = new ArrayList<>();
        if (!imgsData.isEmpty()) {
            String imgSrcData = imgsData.get(0).getData();
            imgPaths = Arrays.asList(imgSrcData.split(","));
        }

        Map<String, Object> result = new HashMap<>();

        // 从 0 时开始，每次 + 6
        int hour = 0;
        int dayIncrement = 0; // 用于处理跨天情况
        int daysInMonth = 30; // 假设每月固定为30天
        List<String> titlesList = new ArrayList<>();
        for (int i = 0; i < imgPaths.size(); i++)
        {
            if (hour >= 24) {
                hour -= 24;
                dayIncrement += 1;
            }

            int newDay = Integer.parseInt(day) + dayIncrement;
            int newMonth = Integer.parseInt(month);
            if (newDay > daysInMonth) {
                newDay -= daysInMonth;
                newMonth++;
            }

            titlesList.add(String.format("%s年%s月%s日%d时2米气温预测结果", year, newMonth, newDay, hour));
            hour+= 6;
        }

        List<String> testsList = new ArrayList<>();
        for (int i = 0; i < imgPaths.size(); i++)
        {
            testsList.add("全球整体气温偏高，澳大利亚出现异常高温");
        }

        result.put("titles", titlesList);
        result.put("imgSrc", imgPaths);
        result.put("texts", testsList);

        return result;
    }

    /**
     * 获取指定 年、月、日 的图片路径 WEA_TP
     * eg. http://localhost:8080/imgs/WEA_TP/getImgsPath?year=2019&month=1&day=1
     */
    @GetMapping("/WEA_TP/getImgsPath")
    @ApiOperation(notes = "获取指定 年、月、日 的图片路径 WEA_TP", value = "获取指定 年、月、日 的图片路径 WEA_TP")
    public Map<String, Object> getTPImgsPathByDay(String year, String month, String day) {
        List<Imgs> imgsData = imgsMapper.findImgsInfoByDayType(year, month, day, "WEA_TP");

        // 将 data 字段从 JSON 字符串转换为 List<String>
        List<String> imgPaths = new ArrayList<>();
        if (!imgsData.isEmpty()) {
            String imgSrcData = imgsData.get(0).getData();
            imgPaths = Arrays.asList(imgSrcData.split(","));
        }

        Map<String, Object> result = new HashMap<>();

        // 从 0 时开始，每次 + 6
        int hour = 0;
        int dayIncrement = 0; // 用于处理跨天情况
        int daysInMonth = 30; // 假设每月固定为30天
        List<String> titlesList = new ArrayList<>();
        for (int i = 0; i < imgPaths.size(); i++)
        {
            if (hour >= 24) {
                hour -= 24;
                dayIncrement += 1;
            }

            int newDay = Integer.parseInt(day) + dayIncrement;
            int newMonth = Integer.parseInt(month);
            if (newDay > daysInMonth) {
                newDay -= daysInMonth;
                newMonth++;
            }

            titlesList.add(String.format("%s年%s月%s日%d时地表降水预测结果", year, newMonth, newDay, hour));
            hour+= 6;
        }

        List<String> testsList = new ArrayList<>();
        for (int i = 0; i < imgPaths.size(); i++)
        {
            testsList.add("赤道地区与北半球降水较多，南半球降水较少");
        }

        result.put("titles", titlesList);
        result.put("imgSrc", imgPaths);
        result.put("texts", testsList);

        return result;
    }

    /**
     * 获取指定 年、月、日 的图片路径 WEA_U10
     * eg. http://localhost:8080/imgs/WEA_U10/getImgsPath?year=2019&month=1&day=1
     */

    @GetMapping("/WEA_U10/getImgsPath")
    @ApiOperation(notes = "获取指定 年、月、日 的图片路径 WEA_U10", value = "获取指定 年、月、日 的图片路径 WEA_U10")
    public Map<String, Object> getU10ImgsPathByDay(String year, String month, String day) {
        List<Imgs> imgsData = imgsMapper.findImgsInfoByDayType(year, month, day, "WEA_U10");

        // 将 data 字段从 JSON 字符串转换为 List<String>
        List<String> imgPaths = new ArrayList<>();
        if (!imgsData.isEmpty()) {
            String imgSrcData = imgsData.get(0).getData();
            imgPaths = Arrays.asList(imgSrcData.split(","));
        }

        Map<String, Object> result = new HashMap<>();

        // 从 0 时开始，每次 + 6
        int hour = 0;
        int dayIncrement = 0; // 用于处理跨天情况
        int daysInMonth = 30; // 假设每月固定为30天
        List<String> titlesList = new ArrayList<>();
        for (int i = 0; i < imgPaths.size(); i++)
        {
            if (hour >= 24) {
                hour -= 24;
                dayIncrement += 1;
            }

            int newDay = Integer.parseInt(day) + dayIncrement;
            int newMonth = Integer.parseInt(month);
            if (newDay > daysInMonth) {
                newDay -= daysInMonth;
                newMonth++;
            }

            titlesList.add(String.format("%s年%s月%s日%d时待定待定", year, newMonth, newDay, hour));
            hour+= 6;
        }

        List<String> testsList = new ArrayList<>();
        for (int i = 0; i < imgPaths.size(); i++)
        {
            testsList.add("待定待定");
        }

        result.put("titles", titlesList);
        result.put("imgSrc", imgPaths);
        result.put("texts", testsList);

        return result;
    }

    /**
     * 初始化：返回可选年、月、日范围 WEA_MSLP
     * eg. http://localhost:8080/imgs/WEA_MSLP/getInitData
     */
    @GetMapping("/WEA_MSLP/getInitData")
    @ApiOperation(notes = "初始化：返回可选年、月、日范围 WEA_MSLP", value = "初始化：返回可选年、月、日范围 WEA_MSLP")
    public Map<String, Object> getMSLPInitMonth()
    {
        List<Imgs> imgsData = imgsMapper.findImgsInfoByType("WEA_MSLP");

        String earliestDate = null;
        String latestDate = null;

        for (Imgs img : imgsData) {
            String date = img.getYear() + "-" + img.getMonth() + "-" + img.getDay();
            if (earliestDate == null || date.compareTo(earliestDate) < 0) {
                earliestDate = date;
            }
            if (latestDate == null || date.compareTo(latestDate) > 0) {
                latestDate = date;
            }
        }
        Map<String, Object> result = new HashMap<>();
        result.put("earliestDate", earliestDate);
        result.put("latestDate", latestDate);
        return result;
    }

    /**
     * 初始化：返回可选年、月、日范围 WEA_T2M
     * http://localhost:8080/imgs/WEA_T2M/getInitData
     */
    @GetMapping("/WEA_T2M/getInitData")
    @ApiOperation(notes = "初始化：返回可选年、月、日范围 WEA_T2M", value = "初始化：返回可选年、月、日范围 WEA_T2M")
    public Map<String, Object> getT2MInitMonth()
    {
        List<Imgs> imgsData = imgsMapper.findImgsInfoByType("WEA_T2M");

        String earliestDate = null;
        String latestDate = null;

        for (Imgs img : imgsData) {
            String date = img.getYear() + "-" + img.getMonth() + "-" + img.getDay();
            if (earliestDate == null || date.compareTo(earliestDate) < 0) {
                earliestDate = date;
            }
            if (latestDate == null || date.compareTo(latestDate) > 0) {
                latestDate = date;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("earliestDate", earliestDate);
        result.put("latestDate", latestDate);
        return result;
    }

    /**
     * 初始化：返回可选年、月、日范围 WEA_TP
     * eg. http://localhost:8080/imgs/WEA_TP/getInitData
     */
    @GetMapping("/WEA_TP/getInitData")
    @ApiOperation(notes = "初始化：返回可选年、月、日范围 WEA_TP", value = "初始化：返回可选年、月、日范围 WEA_TP")
    public Map<String, Object> getTPInitMonth()
    {
        List<Imgs> imgsData = imgsMapper.findImgsInfoByType("WEA_TP");

        String earliestDate = null;
        String latestDate = null;

        for (Imgs img : imgsData) {
            String date = img.getYear() + "-" + img.getMonth() + "-" + img.getDay();
            if (earliestDate == null || date.compareTo(earliestDate) < 0) {
                earliestDate = date;
            }
            if (latestDate == null || date.compareTo(latestDate) > 0) {
                latestDate = date;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("earliestDate", earliestDate);
        result.put("latestDate", latestDate);
        return result;
    }

    /**
     * 初始化：返回可选年、月、日范围 WEA_U10
     * eg. http://localhost:8080/imgs/WEA_U10/getInitData
     */
    @GetMapping("/WEA_U10/getInitData")
    @ApiOperation(notes = "初始化：返回可选年、月、日范围 WEA_U10", value = "初始化：返回可选年、月、日范围 WEA_U10")
    public Map<String, Object> getU10InitMonth()
    {
        List<Imgs> imgsData = imgsMapper.findImgsInfoByType("WEA_U10");

        String earliestDate = null;
        String latestDate = null;

        for (Imgs img : imgsData) {
            String date = img.getYear() + "-" + img.getMonth() + "-" + img.getDay();
            if (earliestDate == null || date.compareTo(earliestDate) < 0) {
                earliestDate = date;
            }
            if (latestDate == null || date.compareTo(latestDate) > 0) {
                latestDate = date;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("earliestDate", earliestDate);
        result.put("latestDate", latestDate);
        return result;
    }

    @Autowired
    private ImgsMapperEnso ImgsMapperEnso;
    /**
     * 初始化：返回可选年、月、日范围 WEA_U10
     * eg. http://localhost:8080/imgs/predictionResult/ssta/getInitData
     */
    @GetMapping("/predictionResult/ssta/getInitData")
    @ApiOperation(notes = "初始化：返回可选年、月、日范围 Enso_ssta", value = "初始化：返回可选年、月、日范围 Enso")
    public Map<String, Object> getSSTAInitMonth()
    {
        List<Imgs> imgsData = ImgsMapperEnso.findImgsInfoALL("ENSO_ASC");

        String earliestDate = null;
        String latestDate = null;

        for (Imgs img : imgsData) {
            String date = img.getYear() + "-" + img.getMonth();
            if (earliestDate == null || date.compareTo(earliestDate) < 0) {
                earliestDate = date;
            }
            if (latestDate == null || date.compareTo(latestDate) > 0) {
                latestDate = date;
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("start", earliestDate);
        result.put("end", latestDate);
        return result;
    }
    /**
     * 获取指定 年、月、index的图片路径
     * eg. http://localhost:9090/imgs/WEA_U10/getImgsPath?year=2019&month=1&day=1
     */
    @GetMapping("/predictionResult/ssta")
    public Map<String,  Object> getSstaData(String year, String month, String day) {
        List<Imgs> imgsData1 = ImgsMapperEnso.findImgsInfoByDayType(year, month,"ENSO_ASC");
        List<Imgs> imgsData2 = ImgsMapperEnso.findImgsInfoByDayType(year, month,"ENSO_MC");
        List<Imgs> imgsData3 = ImgsMapperEnso.findImgsInfoByDayType(year, month,"ENSO_GTC");
        Map<String, Object> result = new HashMap<>();
        int length1 = 0;
        int length2 = 0;
        int length3 = 0;
        // 将 data 字段从 JSON 字符串转换为 List<String>
        List<String> imgPaths = new ArrayList<>();
        List<String> imgPaths1 = new ArrayList<>();
        List<String> imgPaths2 = new ArrayList<>();
        List<String> imgPaths3 = new ArrayList<>();
        String title1 =year+"年"+month+"月 ENSO_ASC 预测结果";
        String title2 =year+"年"+month+"月 ENSO_MC 预测结果";
        String title3 =year+"年"+month+"月 ENSO_GTC 预测结果";
        List<String> titles=new ArrayList<>();
        if (!imgsData1.isEmpty()) {
            String imgSrcData1 = imgsData1.get(0).getData();
            imgPaths1 = Arrays.asList(imgSrcData1.split(","));
            length1 = imgPaths1.size();
            for (int i = 0; i < length1; i++) {
                imgPaths.add(imgPaths1.get(i));
                titles.add(title1);
            }
        }
        if (!imgsData2.isEmpty()) {
            String imgSrcData2 = imgsData2.get(0).getData();
            imgPaths2 = Arrays.asList(imgSrcData2.split(","));
            length2 = imgPaths2.size();
            for (int i = 0; i < length2; i++) {
                imgPaths.add(imgPaths2.get(i));
                titles.add(title2);
            }
        }
        if (!imgsData3.isEmpty()) {
            String imgSrcData3 = imgsData3.get(0).getData();
            imgPaths3 = Arrays.asList(imgSrcData3.split(","));
            length3 = imgPaths3.size();
            for (int i = 0; i < length3; i++) {
                imgPaths.add(imgPaths3.get(i));
                titles.add(title3);
            }
        }
        result.put("data", imgPaths);
        result.put("titles", titles);
        return result;
    }

}
