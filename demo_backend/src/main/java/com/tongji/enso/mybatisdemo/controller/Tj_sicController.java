package com.tongji.enso.mybatisdemo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.entity.online.Imgs;
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
        if (data == null || data.trim().isEmpty()) {
            return Collections.emptyList();
        }
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

        Map<String, Object> sicMap=new HashMap<>();
        ObjectMapper objectMapper = new ObjectMapper();
        double []data = null;
        for(Tj_sic sic:sicList){
            try {
                String jsonString = sic.getData();
                data = objectMapper.readValue(jsonString, double[].class);
                sicMap.put(sic.getVar_model(),data);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        return sicMap;
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
        List<Imgs> sicImages = new ArrayList<>(imgsservice.findAllByType("SIC"));
        sicImages.removeIf(item -> item.getYear() == null
                || item.getMonth() == null
                || item.getDay() == null
                || item.getData() == null
                || item.getData().isEmpty());
        sicImages.sort(Comparator
                .comparingInt((Imgs item) -> Integer.parseInt(item.getYear()))
                .thenComparingInt(item -> Integer.parseInt(item.getMonth()))
                .thenComparingInt(item -> Integer.parseInt(item.getDay())));

        LinkedHashMap<String, Imgs> uniqueDates = new LinkedHashMap<>();
        for (Imgs item : sicImages) {
            uniqueDates.put(
                    item.getYear() + "-" + item.getMonth() + "-" + item.getDay(),
                    item
            );
        }
        List<Imgs> availableImageDates = new ArrayList<>(uniqueDates.values());

        HashMap<String, Object> result = new LinkedHashMap<>();
        if (availableImageDates.isEmpty()) {
            result.put("yearList", Collections.emptyList());
            result.put("monthList", Collections.emptyList());
            result.put("dateList", Collections.emptyList());
            result.put("availableDates", Collections.emptyList());
            result.put("sicInitial", Collections.emptyList());
            return result;
        }

        Imgs latest = availableImageDates.get(availableImageDates.size() - 1);
        String defaultYear = latest.getYear();
        String defaultMonth = latest.getMonth();
        String defaultDay = latest.getDay();

        List<Map<String, String>> availableDates = new ArrayList<>();
        LinkedHashSet<String> years = new LinkedHashSet<>();
        LinkedHashSet<String> latestYearMonths = new LinkedHashSet<>();
        LinkedHashSet<String> latestMonthDays = new LinkedHashSet<>();

        for (Imgs item : availableImageDates) {
            Map<String, String> date = new LinkedHashMap<>();
            date.put("year", item.getYear());
            date.put("month", item.getMonth());
            date.put("day", item.getDay());
            availableDates.add(date);
            years.add(item.getYear());

            if (defaultYear.equals(item.getYear())) {
                latestYearMonths.add(item.getMonth());
            }
            if (defaultYear.equals(item.getYear())
                    && defaultMonth.equals(item.getMonth())) {
                latestMonthDays.add(item.getDay());
            }
        }

        result.put("yearList", new ArrayList<>(years));
        result.put("monthList", new ArrayList<>(latestYearMonths));
        result.put("dateList", new ArrayList<>(latestMonthDays));
        result.put("availableDates", availableDates);
        result.put("defaultYear", defaultYear);
        result.put("defaultMonth", defaultMonth);
        result.put("defaultDay", defaultDay);
        result.put("sicInitial", splitCommaSeparatedString(latest.getData()));
        return result;
    }

    /**
     * 查询SIC预测结果误差折线图的可查询日期和最新结果
     */
    @GetMapping("/initial/SICError")
    @ApiOperation(notes = "SIC预测结果误差可查询日期和最新结果", value = "查询SIC预测结果误差折线图的可查询日期和最新结果")
    public HashMap<String,Object> initialSICerror(){
        List<Map<String, String>> availableMonths = tj_sicservice.findErrorAvailableMonths();

        HashMap<String, Object> result = new LinkedHashMap<>();
        result.put("availableMonths", availableMonths);
        if (availableMonths.isEmpty()) {
            result.put("yearList", Collections.emptyList());
            result.put("monthList", Collections.emptyList());
            result.put("dateList", Collections.emptyList());
            result.put("SICerrorInitial", Collections.emptyMap());
            return result;
        }

        Map<String, String> latest = availableMonths.get(availableMonths.size() - 1);
        String defaultYear = latest.get("year");
        String defaultMonth = latest.get("month");
        LinkedHashSet<String> years = new LinkedHashSet<>();
        LinkedHashSet<String> latestYearMonths = new LinkedHashSet<>();
        for (Map<String, String> date : availableMonths) {
            years.add(date.get("year"));
            if (defaultYear.equals(date.get("year"))) {
                latestYearMonths.add(date.get("month"));
            }
        }

        result.put("yearList", new ArrayList<>(years));
        result.put("monthList", new ArrayList<>(latestYearMonths));
        result.put("dateList", Collections.singletonList("1"));
        result.put("defaultYear", defaultYear);
        result.put("defaultMonth", defaultMonth);
        result.put("SICerrorInitial", findSICErrorByMonth(defaultYear, defaultMonth));
        return result;
    }

    /**
     * 查询SIC回报结果误差箱型图的可查询日期和最新结果
     */
    @GetMapping("/initial/SICErrorBox")
    @ApiOperation(notes = "SIC回报结果误差可查询日期和最新结果", value = "查询SIC回报结果误差箱型图的可查询日期和最新结果")
    public Map<String,Object> initialSICerrorbox(){
        List<String> yearList = tj_sicservice.findErrorBoxAvailableYears();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yearList", yearList);
        result.put("monthList", Collections.singletonList("1"));
        result.put("dateList", Collections.singletonList("1"));
        if (yearList.isEmpty()) {
            result.put("SICerrorboxInitial", Collections.emptyMap());
            return result;
        }

        String defaultYear = yearList.get(yearList.size() - 1);
        result.put("defaultYear", defaultYear);
        result.put("SICerrorboxInitial", findSICErrorBoxByYear(defaultYear));
        return result;
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
