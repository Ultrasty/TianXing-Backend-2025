package com.tongji.enso.mybatisdemo.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.entity.online.Meteo;
import com.tongji.enso.mybatisdemo.entity.online.Tj_sie;
import com.tongji.enso.mybatisdemo.service.online.Tj_sieService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/seaice")
public class Tj_sieController {
    @Autowired
    private Tj_sieService tj_sieService;

    /**
     * 查询全部SIE指数
     */
    @GetMapping("/findAll/SIE")
    @ApiOperation(notes = "查询所有月份的SIE指数，按id排序", value = "查询全部SIE指数")
    public List<Tj_sie> findAll(){
        return tj_sieService.findAllSIE();
    }

    /**
     * 查询某月份开始起报之后12个月的SIE指数
     */
    @GetMapping("/predictionResult/SIE")
    @ApiOperation(notes = "查询月份开始起报之后12个月的SIE指数以及文本描述", value = "根据月份查询SIE指数预测结果")
    public HashMap<String,Object> findByMonth(@RequestParam String year, @RequestParam String month){

        // 要返回的对象列表
        HashMap<String, Object> return_hashmap = new HashMap<String, Object>();

        HashMap<String, Object> option = new  HashMap<String, Object>();
        return_hashmap.put("option",option);
        
        //修改：删除硬编码的availableList
        //List<HashMap<String,Object>> availableList = new ArrayList<>();
        //availableList.add(new HashMap<>());
        //availableList.get(0).put("year",2023);
        //availableList.get(0).put("month",1);
        //return_hashmap.put("availableList",availableList);

        HashMap<String, Object> title = new  HashMap<String, Object>();
        //String next_year=Integer.parseInt(year)+1+"";
        //String end_month=month.equals("1") ? "12" : String.valueOf(Integer.parseInt(month) - 1);//修改，确保跨年时正确
        //if(Integer.parseInt(month)>1)
        //  title.put("text",year+"年"+month+"月~"+next_year+"年"+end_month+"月SIE指数预测结果");
        //else
        //  title.put("text",year+"年1月~"+year+"年12月SIE指数预测结果");
        
        int yearInt = Integer.parseInt(year);
        int monthInt = Integer.parseInt(month);

        // 计算结束年月
        LocalDate startDate = LocalDate.of(yearInt, monthInt, 1);
        LocalDate endDate = startDate.plusMonths(11);
        String endYear = String.valueOf(endDate.getYear());
        String endMonth = String.valueOf(endDate.getMonthValue());
        // 设置标题
        title.put("text", year + "年" + month + "月~" + endYear + "年" + endMonth + "月SIE指数预测结果");
        title.put("left","center");
        option.put("title",title);

        HashMap<String, Object> tooltip = new  HashMap<String, Object>();
        option.put("tooltip",tooltip);

        HashMap<String, Object> xAxis = new  HashMap<String, Object>();
        xAxis.put("type","category");
        xAxis.put("name","时间");
        
        String[] xAxis_data={"一月", "二月", "三月", "四月", "五月","六月", "七月", "八月", "九月", "十月", "十一月", "十二月"};
        List<String> monthList = new ArrayList<>();
        int currentMonth = monthInt; // 当前起始月份

        for (int i = 0; i < 12; i++) {
            // 添加当前月份的中文名称（注意：月份值-1对应数组索引）
            monthList.add(xAxis_data[currentMonth - 1]);

            // 移动到下一个月（达到12月后重置为1月）
            currentMonth = (currentMonth % 12) + 1;
        }

        xAxis.put("data", monthList);
        option.put("xAxis", xAxis);

        

        HashMap<String, Object> yAxis = new  HashMap<String, Object>();
        yAxis.put("type","value");
        option.put("yAxis",yAxis);

        // 查找的对象列表
        List<Tj_sie> sieList = tj_sieService.findSIEByMonth(year, month);
        String[] order = {"prediction_IceTFT", "mean_IceTFT", "upper_IceTFT", "lower_IceTFT"};
        Map<String, String> seriesNames = new HashMap<>();
        seriesNames.put("prediction_IceTFT", "prediction");
        seriesNames.put("mean_IceTFT", "mean");
        seriesNames.put("upper_IceTFT", "upper");
        seriesNames.put("lower_IceTFT", "lower");
        sieList.sort(Comparator.comparingInt(item -> Arrays.asList(order).indexOf(item.getVar_model())));

        List<HashMap<String, Object>> series= new ArrayList<>();
        List<String> legendData = new ArrayList<>();
        // 使用ObjectMapper进行JSON数据解析
        ObjectMapper objectMapper = new ObjectMapper();
        // 遍历返回结果中的每个Tj_sie对象，对其data字段进行解析，并替换为一维数组
        for (Tj_sie sie : sieList) {
            String seriesName = seriesNames.get(sie.getVar_model());
            if (seriesName == null) {
                continue;
            }

            String jsonData = sie.getData(); // 获取JSON数据的字符串形式
            try {
                // 将JSON数据转换为一维double数组
                double[] dataArray = objectMapper.readValue(jsonData, double[].class);
                // 将解析后的一维数组设置到Tj_sie对象的data字段中
                sie.setTrans_data(dataArray);
                HashMap<String, Object> currentSeries = new HashMap<>();
                currentSeries.put("name", seriesName);
                currentSeries.put("type", "line");
                currentSeries.put("data", dataArray);
                series.add(currentSeries);
                legendData.add(seriesName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        HashMap<String, Object> legend = new HashMap<>();
        legend.put("data", legendData);
        legend.put("orient", "horizontal");
        legend.put("left", "center");
        legend.put("bottom", "5");
        option.put("legend", legend);

        // 动态生成描述文本
        double minValue = Double.MAX_VALUE;
        int minIndex = -1;
        for (Tj_sie sie : sieList) {
            if ("prediction_IceTFT".equals(sie.getVar_model()) && sie.getTrans_data() != null) {
                for (int i = 0; i < sie.getTrans_data().length; i++) {
                    if (sie.getTrans_data()[i] < minValue) {
                        minValue = sie.getTrans_data()[i];
                        minIndex = i;
                    }
                }
            }
        }
        option.put("series",series);
        if (minIndex >= 0) {
            LocalDate minimumDate = startDate.plusMonths(minIndex);
            return_hashmap.put(
                "description",
                String.format(
                    "%d年%d月SIE预测极小值为%.4f。",
                    minimumDate.getYear(),
                    minimumDate.getMonthValue(),
                    minValue
                )
            );
        } else {
            return_hashmap.put("description", "该月份暂无SIE预测数据。");
        }
        

        
        return_hashmap.put("option",option);
        //return_hashmap.put("description","2023年9月SIE极小值预测为4.4133，相较于2022年观测偏低，2023年海冰范围预计将比2022年整体偏少。");
        return return_hashmap;
    }

    /**
     * 查询指定var_model以及指定月份月份开始起报之后12个月的SIE数据
     */
    @GetMapping("/findByModelandTime/SIE")
    @ApiOperation(notes = "查询指定var_model以及指定年月的SIE数据(已转化为一维数组形式）", value = "根据年月和var_model查询SIE指数预测结果")
    public HashMap<String,Object> findByModelandMonth(@RequestParam String year, @RequestParam String month, @RequestParam String var_model){

        // 要返回的对象列表
        List<Tj_sie> sieList = tj_sieService.findByModelandMonth(year, month,var_model);
        ObjectMapper objectMapper = new ObjectMapper();
        double[] dataArray = null;
        // 遍历返回结果中的每个Tj_sie对象，对其data字段进行解析，并替换为一维数组
        for (Tj_sie sie : sieList) {
            String jsonData = sie.getData(); // 获取JSON数据的字符串形式
            try {
                // 将JSON数据转换为一维double数组
                dataArray = objectMapper.readValue(jsonData, double[].class);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        HashMap<String, Object> return_hashmap = new HashMap<String, Object>();
        return_hashmap.put("data", dataArray);
        return return_hashmap;
    }

    /**
     * 查询年份及其前几年的rmsd和相关系数等指标的数据，文本描述
     */
    @GetMapping("/predictionExamination/errorAnalysis")
    @ApiOperation(value = "SIE预测误差分析", notes = "查询年份及其前几年的rmsd和相关系数等指标的数据，文本描述")
    public HashMap<String ,Object> findErrorAnalysis(@RequestParam String year){
        // 要返回的对象列表
        List<Tj_sie> sieList = tj_sieService.findByYear(year);
        HashMap<String, Object> return_hashmap = new HashMap<String, Object>();
        ObjectMapper objectMapper = new ObjectMapper();
        double[] dataArray = null;
        // 遍历返回结果中的每个Tj_sie对象，对其data字段进行解析，并替换为一维数组
        for (Tj_sie sie : sieList) {
            String jsonData = sie.getData(); // 获取JSON数据的字符串形式
            try {
                // 将JSON数据转换为一维double数组
                dataArray = objectMapper.readValue(jsonData, double[].class);
                return_hashmap.put(sie.getVar_model(), dataArray);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return return_hashmap;
    }

    /**
     * 查询SIE预测结果图的可查询日期和最新预报
     */
    @GetMapping("/initial/SIEprediction")
    @ApiOperation(value = "SIE可查询日期与最新预报结果", notes = "查询SIE指数预测结果图的可查询日期和最新预报")
    public HashMap<String ,Object> initialSIEprediction(){
        List<Tj_sie> predictionRows = new ArrayList<>(tj_sieService.findAllSIE());
        predictionRows.removeIf(item -> !"prediction_IceTFT".equals(item.getVar_model())
                || item.getYear() == null
                || item.getMonth() == null);
        predictionRows.sort(Comparator
                .comparingInt((Tj_sie item) -> Integer.parseInt(item.getYear()))
                .thenComparingInt(item -> Integer.parseInt(item.getMonth())));

        LinkedHashMap<String, Tj_sie> uniqueMonths = new LinkedHashMap<>();
        for (Tj_sie item : predictionRows) {
            uniqueMonths.put(item.getYear() + "-" + item.getMonth(), item);
        }
        List<Tj_sie> availablePredictionMonths = new ArrayList<>(uniqueMonths.values());

        HashMap<String, Object> return_hashmap = new LinkedHashMap<>();
        if (availablePredictionMonths.isEmpty()) {
            return_hashmap.put("yearList", Collections.emptyList());
            return_hashmap.put("monthList", Collections.emptyList());
            return_hashmap.put("availableMonths", Collections.emptyList());
            return_hashmap.put("sieInitial", Collections.emptyList());
            return return_hashmap;
        }

        Tj_sie latest = availablePredictionMonths.get(availablePredictionMonths.size() - 1);
        String latestYear = latest.getYear();
        String latestMonth = latest.getMonth();

        LinkedHashSet<String> availableYears = new LinkedHashSet<>();
        LinkedHashSet<String> availableMonths = new LinkedHashSet<>();
        List<Map<String, String>> exactAvailableMonths = new ArrayList<>();
        for (Tj_sie item : availablePredictionMonths) {
            availableYears.add(item.getYear());
            availableMonths.add(item.getMonth());
            Map<String, String> date = new LinkedHashMap<>();
            date.put("year", item.getYear());
            date.put("month", item.getMonth());
            exactAvailableMonths.add(date);
        }

        // 查询最新数据
        List<Tj_sie> sieList = tj_sieService.findSIEByMonth(latestYear, latestMonth);

        return_hashmap.put("yearList", new ArrayList<>(availableYears));
        return_hashmap.put("monthList", new ArrayList<>(availableMonths));
        return_hashmap.put("availableMonths", exactAvailableMonths);
        return_hashmap.put("defaultYear", latestYear);
        return_hashmap.put("defaultMonth", latestMonth);
        return_hashmap.put("sieInitial", sieList);
        // 使用ObjectMapper进行JSON数据解析
        ObjectMapper objectMapper = new ObjectMapper();
        // 遍历返回结果中的每个Tj_sie对象，对其data字段进行解析，并替换为一维数组
        for (Tj_sie sie : sieList) {
            String jsonData = sie.getData(); // 获取JSON数据的字符串形式
            try {
                // 将JSON数据转换为一维double数组
                double[] dataArray = objectMapper.readValue(jsonData, double[].class);
                // 将解析后的一维数组设置到Tj_sie对象的data字段中
                sie.setTrans_data(dataArray);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return_hashmap.put("sieInitial",sieList);

        return return_hashmap;
    }

    /**
     * 查询SIE预测结果误差折线图的可查询日期和最新结果
     */
    @GetMapping("/initial/SIEErrorAnalysis")
    @ApiOperation(notes = "SIE预测误差分析可查询日期和最新结果", value = "查询SIE误差分析图的可查询日期和最新结果")
    public HashMap<String,Object> initialSIEerrorAnalysis(){
        List<String> yearList = tj_sieService.findErrorAnalysisAvailableYears();

        HashMap<String, Object> result = new LinkedHashMap<>();
        result.put("yearList", yearList);
        result.put("monthList", Collections.singletonList("1"));
        if (yearList.isEmpty()) {
            result.put("SIEerrorInitial", Collections.emptyMap());
            return result;
        }

        String defaultYear = yearList.get(yearList.size() - 1);
        result.put("defaultYear", defaultYear);
        result.put("SIEerrorInitial", findErrorAnalysis(defaultYear));
        return result;
    }
    @ExceptionHandler(DataNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(DataNotFoundException ex) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "请求数据不存在");
        response.put("message", ex.getMessage());
        response.put("timestamp", LocalDateTime.now());
        return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
    }

    // 新增自定义异常类
    static class DataNotFoundException extends RuntimeException {
        public DataNotFoundException(String message) {
            super(message);
        }
    }
}
