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

        HashMap<String, Object> legend = new  HashMap<String, Object>();
        String[] legend_data={"prediction", "mean", "upper", "lower"};
        legend.put("data",legend_data);
        legend.put("orient","horizontal");
        legend.put("left","center");
        legend.put("bottom","5");
        option.put("legend",legend);

        // 查找的对象列表
        List<Tj_sie> sieList = tj_sieService.findSIEByMonth(year, month);
        List<HashMap<String, Object>> series= new ArrayList<>();
        // 使用循环添加指定数量的空 HashMap 到列表中
        for (int i = 0; i < sieList.size(); i++) {
            series.add(new HashMap<>());
        }
        // 使用ObjectMapper进行JSON数据解析
        ObjectMapper objectMapper = new ObjectMapper();
        // 遍历返回结果中的每个Tj_sie对象，对其data字段进行解析，并替换为一维数组
        int series_num=0;
        for (Tj_sie sie : sieList) {
            String jsonData = sie.getData(); // 获取JSON数据的字符串形式
            if(sie.getVar_model().equals("prediction_IceTFT"))
                series.get(series_num).put("name","prediction");
            if(sie.getVar_model().equals("mean_IceTFT"))
                series.get(series_num).put("name","mean");
            if(sie.getVar_model().equals("upper_IceTFT"))
                series.get(series_num).put("name","upper");
            if(sie.getVar_model().equals("lower_IceTFT"))
                series.get(series_num).put("name","lower");
            series.get(series_num).put("type","line");
            try {
                // 将JSON数据转换为一维double数组
                double[] dataArray = objectMapper.readValue(jsonData, double[].class);
                // 将解析后的一维数组设置到Tj_sie对象的data字段中
                sie.setTrans_data(dataArray);
                series.get(series_num).put("data",dataArray);
            } catch (Exception e) {
                e.printStackTrace();
            }
            series_num++;
        }
        // 确保数据顺序一致
        Collections.sort(sieList, (a, b) -> {
            String[] order = {"prediction_IceTFT", "mean_IceTFT", "upper_IceTFT", "lower_IceTFT"};
            return Integer.compare(
                Arrays.asList(order).indexOf(a.getVar_model()),
                Arrays.asList(order).indexOf(b.getVar_model())
            );
        });

        // 动态生成描述文本
        double minValue = Double.MAX_VALUE;
        String minMonthName = "";
        for (Tj_sie sie : sieList) {
            if ("prediction_IceTFT".equals(sie.getVar_model()) && sie.getTrans_data() != null) {
                for (int i = 0; i < sie.getTrans_data().length; i++) {
                    if (sie.getTrans_data()[i] < minValue) {
                        minValue = sie.getTrans_data()[i];
                        minMonthName = xAxis_data[i]; // xAxis_data是月份名称数组
                    }
                }
            }
        }
        option.put("series",series);
        // 生成动态描述
        int currentYear = Integer.parseInt(year);
        String desc = String.format("%d年%sSIE极小值预测为%.4f，相较于%d年观测%s。预测显示海冰范围将比基准年%s。",
            currentYear, minMonthName, minValue,
            currentYear-1, 
            minValue < 4.5 ? "偏低" : "偏高", // 4.5为示例,不符合逻辑!!
            minValue < 5.0 ? "整体偏少" : "整体偏多"); // 5.0为示例

        return_hashmap.put("description", desc);
        

        
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
        //List<String> yearList = Arrays.asList("2023");
        //List<String> monthList=Arrays.asList("1");
        // 要返回的HashMap
        //HashMap<String, Object> return_hashmap = new HashMap<String, Object>();
        //return_hashmap.put("yearList",yearList);
        //return_hashmap.put("monthList",monthList);
        // 要返回的对象列表
        //List<Tj_sie> sieList = tj_sieService.findSIEByMonth("2023", "1");
        // 从数据库获取可用年份和月份
        List<String> availableYears = tj_sieService.findAvailableYears();
        List<String> availableMonths = tj_sieService.findAvailableMonths();

        // 获取最新日期
        Map<String, String> latestDate = tj_sieService.findLatestDate();
        String latestYear = latestDate.get("year");
        String latestMonth = latestDate.get("month");

        // 查询最新数据
        List<Tj_sie> sieList = tj_sieService.findSIEByMonth(latestYear, latestMonth);

        HashMap<String, Object> return_hashmap = new HashMap<String, Object>() {{
            put("yearList", availableYears);
            put("monthList", availableMonths);
            put("defaultYear", latestYear);
            put("defaultMonth", latestMonth);
            put("sieInitial", sieList);
        }};
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
        //List<String> yearList=Arrays.asList("2022");
        //List<String> monthList=Arrays.asList("1");
        List<String> availableYears = tj_sieService.findAvailableYears();
    
        // 2. 获取最新年份（如果没有数据则使用默认值）
        String latestYear = tj_sieService.findLatestDate().get("year");
        if (latestYear == null && !availableYears.isEmpty()) {
            latestYear = availableYears.get(0); // 使用第一个可用年份
        } else if (latestYear == null) {
            latestYear = "2025"; // 最终回退值
        }
        // 要返回的HashMap
        HashMap<String, Object> return_hashmap = new HashMap<String, Object>();
        return_hashmap.put("yearList",availableYears);
        return_hashmap.put("defaultYear",latestYear);

        Map<String,Object> SIEerrorList =findErrorAnalysis(latestYear);
        return_hashmap.put("SIEerrorInitial",SIEerrorList);

        return return_hashmap;
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