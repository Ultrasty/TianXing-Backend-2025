package com.tongji.enso.mybatisdemo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.entity.online.Tj_sic;
import com.tongji.enso.mybatisdemo.service.online.ImgsService;
import com.tongji.enso.mybatisdemo.service.online.Tj_sicService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/seaice")
public class Tj_sicController {

    @Autowired
    private Tj_sicService tj_sicservice;
    @Autowired
    private ImgsService imgsservice;

    /**
     * 查询全部的SIC指数
     */
    @GetMapping("/findAllSIC")
    @ApiOperation(notes = "查询全部Sic指数，返回'Tj_sic'类型列表", value = "查询全部Sic指数")
    public List<Tj_sic> findAll(){ return tj_sicservice.findAllSIC(); }

    /**
     * 查询某年某月某日的SIC指数的模块图
     * @param: year, month, day;
     * @return: List<String>.
     */
    @GetMapping("/predictionResult/SIC")
    @ApiOperation(notes = "查询指定日期SIC指数的模块图", value = "根据日期查询SIC指数模块图的地址")
    public List<String> findSICPredictionByDate(@RequestParam String year,@RequestParam String month,@RequestParam String day){

        String data = imgsservice.findSICImgByDate(year,month,day);
        int index = 0;
        List<String> sicList=new ArrayList<>();

        for(int i = 0; i<data.length();i++){
            char c = data.charAt(i);
            if(c == ','){
                sicList.add(data.substring(index,i));
                index = i + 1;
            }
        }
        sicList.add(data.substring(index));

        return sicList;
    }

    @GetMapping("/error")
    @ApiOperation(notes = "查询月份4周的SIC预测结果与基线方法的比较以及文本描述", value = "根据月份查询SIC指数预测结果与基线方法的比较")
    public Map<String, Object> findSICErrorByMonth(@RequestParam String year,@RequestParam String month){

        List<Tj_sic> sicList=tj_sicservice.findErrorByMonth(year,month);

        Map<String, List<Double>> grouped = new LinkedHashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        for(Tj_sic sic:sicList){
            try {
                String jsonString = sic.getData();
                double[] data = objectMapper.readValue(jsonString, double[].class);
                List<Double> values = grouped.computeIfAbsent(sic.getVar_model(), key -> new ArrayList<>());
                for (double value : data) values.add(value);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.putAll(grouped);
        return response;
    }

    @GetMapping("/errorBox")
    @ApiOperation(notes="查询年份四种SIC预测结果提前1到7天的统计结果误差箱型图数据，文本描述",value="查询sic误差箱型图数据")
    public Map<String, Object> findSICErrorBoxByYear(@RequestParam String year){

        List<Tj_sic> sicList=tj_sicservice.findErrorBoxByYearAndModel(year);

        Map<String, Object> sicMap=new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();


        double []data = null;

        // 遍历查询结果中的每组数据
        for(Tj_sic sic:sicList){
            try {
                String jsonString = sic.getData();
                data = objectMapper.readValue(jsonString, double[].class);  // 将data字段转为一维数组
                int size= data.length / 7;  // size表示数据等分为7组后，每组数据的长度

                // 创建二维数组result存储最终结果，result[0][]表示提前1天的统计结果误差，以此类推
                double [][]result = new double[7][size];
                int j = 0;
                for(int i = 0; i < data.length; i++){
                    result[i % 7][j] = data[i];
                    if (i % 7 == 6) {   // 每取7个数据，j++
                        j++;
                    }
                }
                // 将result放入map中
                sicMap.put(sic.getVar_model(),result);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }

        return sicMap;
    }

    //用于字符串分割
    private List<String> splitCommaSeparatedString(String input) {
    if (input == null || input.isEmpty()) return Collections.emptyList();
    return Arrays.asList(input.split(","));
    }
    /**
     * 查询SIC预测结果图的可查询日期和最新预报结果
     */
    @GetMapping("/initial/SICprediction")
    @ApiOperation(notes = "SIC可查询日期及最新预报结果", value = "查询SIC预测结果图的可查询日期和最新预报结果")
    public HashMap<String,Object> initialSICprediction(){
        //List<String> yearList=Arrays.asList("2023");
        //List<String> monthList=Arrays.asList("6");
        //List<String> dateList=Arrays.asList("5","12","19","26");
        // 获取可用年份
        List<String> yearList = tj_sicservice.getAvailableYears();
        
        // 获取最新日期
        Map<String, String> latestDate = tj_sicservice.getLatestDate();
        if (latestDate == null || latestDate.isEmpty()) {
            return createEmptyResponse("yearList", "monthList", "dateList", "sicInitial");
        }
        
        String defaultYear = latestDate.get("year");
        String defaultMonth = latestDate.get("month");
        String defaultDay = latestDate.get("day");
        
        // 获取默认年份下的月份
        List<String> monthList = tj_sicservice.getAvailableMonths(defaultYear);
        
        // 获取默认年月下的日期
        List<String> dateList = tj_sicservice.getAvailableDays(defaultYear, defaultMonth);
        // 要返回的HashMap
        HashMap<String, Object> return_hashmap = new HashMap<String, Object>();
        return_hashmap.put("yearList",yearList);
        return_hashmap.put("monthList",monthList);
        return_hashmap.put("dateList",dateList);

        //String data = imgsservice.findSICImgByDate("2023","6","26");
        //获取最新预报结果
        String data = imgsservice.findSICImgByDate(defaultYear, defaultMonth, defaultDay);
        //字符串分割逻辑
        // int index = 0;
        // List<String> sicList=new ArrayList<>();

        // for(int i = 0; i<data.length();i++){
        //     char c = data.charAt(i);
        //     if(c == ','){
        //         sicList.add(data.substring(index,i));
        //         index = i + 1;
        //     }
        // }
        // sicList.add(data.substring(index));

        return_hashmap.put("sicInitial",splitCommaSeparatedString(data));

        return return_hashmap;
    }

    /**
     * 查询SIC预测结果误差折线图的可查询日期和最新结果
     */
    @GetMapping("/initial/SICError")
    @ApiOperation(notes = "SIC预测结果误差可查询日期和最新结果", value = "查询SIC预测结果误差折线图的可查询日期和最新结果")
    public HashMap<String,Object> initialSICerror(){
        List<String> yearList=Arrays.asList("2023");
        List<String> monthList=Arrays.asList("1");
        List<String> dateList=Arrays.asList("1");
        // 要返回的HashMap
        HashMap<String, Object> return_hashmap = new HashMap<String, Object>();
        return_hashmap.put("yearList",yearList);
        return_hashmap.put("monthList",monthList);
        return_hashmap.put("dateList",dateList);

        Map<String,Object> SICerrorList =findSICErrorByMonth("2023","1");
        return_hashmap.put("SICerrorInitial",SICerrorList);

        return return_hashmap;
    }

    /**
     * 查询SIC回报结果误差箱型图的可查询日期和最新结果
     */
    @GetMapping("/initial/SICErrorBox")
    @ApiOperation(notes = "SIC回报结果误差可查询日期和最新结果", value = "查询SIC回报结果误差箱型图的可查询日期和最新结果")
    public Map<String,Object> initialSICerrorbox(){
        List<String> yearList=Arrays.asList("2022");
        List<String> monthList=Arrays.asList("1");
        List<String> dateList=Arrays.asList("1");
        // 要返回的HashMap
        Map<String, Object> return_hashmap = new HashMap<String, Object>();
        return_hashmap.put("yearList",yearList);
        return_hashmap.put("monthList",monthList);
        return_hashmap.put("dateList",dateList);

        Map<String,Object> SICerrorboxList =findSICErrorBoxByYear("2022");
        return_hashmap.put("SICerrorboxInitial",SICerrorboxList);

        return return_hashmap;
    }
    // 空响应创建方法
    private HashMap<String, Object> createEmptyResponse(String... keys) {
        HashMap<String, Object> response = new HashMap<>();
        for (String key : keys) {
            response.put(key, Collections.emptyList());
        }
        return response;
    }

}
