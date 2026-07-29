package com.tongji.enso.mybatisdemo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.enso.mybatisdemo.entity.online.*;
import com.tongji.enso.mybatisdemo.mapper.online.ImgsMapper;
import com.tongji.enso.mybatisdemo.service.online.ImgsService;
import com.tongji.enso.mybatisdemo.service.online.Info_sic_latlonService;
import com.tongji.enso.mybatisdemo.service.online.Obs_naoService;
import com.tongji.enso.mybatisdemo.service.online.Tj_naoService;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.relational.core.dialect.HsqlDbDialect;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@RestController
@RequestMapping("/nao")
public class Tj_naoController {
    private static final Logger logger = LoggerFactory.getLogger(Tj_naoController.class);

    @Autowired
    private Tj_naoService tj_naoservice;
    @Autowired
    private Obs_naoService obs_naoservice;
    @Autowired
    private ImgsService imgsservice;

    /**
     * 查询某月份的NAO指数数据
     * @param: year, month;
     * @return: Map<String, Object>.
     */
    @GetMapping("/predictionResult/nao")
    @ApiOperation(notes = "查询月份开始六个月的NAO指数预测和观测结果以及文本描述", value = "根据月份查询NAO指数预测和观测结果")
    public Map<String,Object> findNAOPredictionByMonth(@RequestParam String year, @RequestParam String month){
        int m;
        try {
            m = Integer.parseInt(month);
            Integer.parseInt(year);
        } catch (NumberFormatException error) {
            return emptyNAOPredictionResult("日期格式无效");
        }
        if (m < 1 || m > 12) {
            return emptyNAOPredictionResult("日期超出可查询范围");
        }

        Tj_nao preResult = tj_naoservice.findPredictionByMonthAndModel(year,month);
        // 数据库中观测数据按年存储，所以查询月份都是1，查出来的是12个月份的数据
        Obs_nao obsResult = obs_naoservice.findObservationByMonthAndModel(year,"1");
        if (preResult == null || preResult.getData() == null) {
            return emptyNAOPredictionResult("该月份暂无NAO指数预测数据");
        }

        Map<String,Object> naoMap = new LinkedHashMap<>();

        // 处理返回数据格式
        String end_year,end_month;
        if(m > 7){
            end_month = Integer.toString(m-7);
            end_year=Integer.toString(Integer.parseInt(year)+1);
        }
        else{
            end_year=year;
            end_month=Integer.toString(m+5);
        }
        Map<String, Object> title=new LinkedHashMap<>();
        title.put("text",String.format("%s年%s月~%s年%s月 NAOI指数预测结果", year, month, end_year, end_month));
        title.put("left","center");
        naoMap.put("title",title);
        Map<String, Object> tooltip=new LinkedHashMap<>();
        naoMap.put("tooltip",tooltip);
        Map<String, Object> xAxis=new LinkedHashMap<>();
        xAxis.put("type","category");
        xAxis.put("name","时间");
        List<String> month_data=new ArrayList<>();
        String[] chinese_month = {"一月","二月","三月","四月","五月","六月","七月","八月","九月","十月","十一月","十二月"};
        int temp = m;
        for(int i=0; i<6; i++) {
            month_data.add(chinese_month[temp - 1]);
            temp++;
            if(temp > 12){
                temp = temp - 12;
            }
        }
        xAxis.put("data",month_data);
        naoMap.put("xAxis",xAxis);
        Map<String,Object> yAxis=new LinkedHashMap<>();
        yAxis.put("name","冬季NAOI");
        yAxis.put("nameLocation","center");
        Map<String,Object> nameTextStyle=new LinkedHashMap<>();
        nameTextStyle.put("frontSize",16);
        int[] padding=new int[4];
        padding[0]=0;
        padding[1]=0;
        padding[2]=15;
        padding[3]=0;
        nameTextStyle.put("padding",padding);
        String p="nameTextStyle";
        yAxis.put(p,nameTextStyle);
        yAxis.put("type","value");
        naoMap.put("yAxis",yAxis);
        Map<String,Object> legend=new LinkedHashMap<>();
        String []legend_data= new String[2];
        legend_data[0]="观测值";
        legend_data[1]="NAO-MCR";
        legend.put("data",legend_data);
        legend.put("orient","horizontal");
        legend.put("left","center");
        legend.put("bottom","5");
        naoMap.put("legend",legend);

        // 处理预报数据和观测数据
        double []pre_data;
        List<Double> obs_data = new ArrayList<>();
        ObjectMapper objectMapper=new ObjectMapper();

        try{
            // 将json形式的预测数据转化为一维数组
            pre_data = objectMapper.readValue(preResult.getData(),double[].class);

            for (int i = 0; i < 6; i++) {
                int monthIndex = m - 1 + i;
                String observationYear = Integer.toString(
                        Integer.parseInt(year) + monthIndex / 12
                );
                int observationMonth = monthIndex % 12;
                Obs_nao yearlyObservation = observationYear.equals(year)
                        ? obsResult
                        : obs_naoservice.findObservationByMonthAndModel(observationYear, "1");

                if (yearlyObservation == null || yearlyObservation.getData() == null) {
                    obs_data.add(null);
                    continue;
                }
                double[] yearlyData = objectMapper.readValue(
                        yearlyObservation.getData(),
                        double[].class
                );
                obs_data.add(observationMonth < yearlyData.length
                        ? yearlyData[observationMonth]
                        : null);
            }
        }catch (JsonProcessingException e){
            logger.error("Failed to parse NAO prediction data", e);
            return emptyNAOPredictionResult("NAO指数预测数据格式异常");
        }

        List<Map<String,Object>> series = new ArrayList<>();
        Map<String,Object> obs_Data=new LinkedHashMap<>();
        Map<String,Object> pre_Data=new LinkedHashMap<>();
        obs_Data.put("name","观测值");
        obs_Data.put("type","line");
        obs_Data.put("data",obs_data);
        series.add(obs_Data);
        pre_Data.put("name","NAO-MCR");
        pre_Data.put("type","line");
        pre_Data.put("data",pre_data);
        series.add(pre_Data);
        naoMap.put("series",series);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("option",naoMap);
        result.put("description","描述暂无");
        return result;
    }

    /**
     * 根据年月返回预测结果误差分布图地址
     * @param: year, month;
     * @return: List<String>.
     */
    @GetMapping("/predictionExamination/nao")
    @ApiOperation(notes = "根据年月返回预测结果误差分布图地址", value = "根据年月返回预测结果误差分布图地址")
    public List<String>findNAOCORRByMonth(@RequestParam String year, @RequestParam String month){
        List<String> naolist=new ArrayList<>();
        String data=imgsservice.findNAOCORRImgByMonth(year,month);
        naolist.add(data);
        return naolist;
    }


    /**
     * 根据年月，返回该时模型起报的6个月格点数据的模块图
     * @param: year, month;
     * @return: List<String>.
     */
     @GetMapping("/findGridData/nao")
     @ApiOperation(notes = "根据年月，返回该时模型起报的6个月格点数据的模块图", value = "根据年月返回格点数据的模块图地址")
     public List<String> findGridByMonth(@RequestParam String year, @RequestParam String month) {
         String data = imgsservice.findNAOImgByMonth(year,month);
         return splitImagePaths(data);
     }

    /**
     * 初始化预报结果折线图
     * 因为观测数据是以年为单位存储的，而每次要返回六个月的数据，所以最晚可查询月份一定是12-5=7月
     * @param: null;
     * @return: Map<String, Object>.
     */
     @GetMapping("/initialize/naoPrediction")

     @ApiOperation(notes = "初始化预报结果折线图，返回可查询年月", value = "初始化预报结果折线图")
     public Map<String, Object> initialNAOPrediction(){
         logger.info("Received request for /nao/initialize/naoPrediction");
         List<Tj_nao> naoList = tj_naoservice.findNAOByModel("index_NAO_MCD");
         Map<String, Object> naoMap=new LinkedHashMap<>();
         Map<String,Object> result=new LinkedHashMap<>();

         if (naoList == null || naoList.isEmpty()) {
             result.put("start_year", null);
             result.put("start_month", null);
             result.put("end_year", null);
             result.put("end_month", null);
             result.put("option", naoMap);
             result.put("description", "暂无可用的 NAO 指数预测数据");
             return result;
         }

         naoList.sort(Comparator
                 .comparingInt((Tj_nao item) -> Integer.parseInt(item.getYear()))
                 .thenComparingInt(item -> Integer.parseInt(item.getMonth())));

         // 返回最早可查询年月和最晚可查询年月
         String end_year=naoList.get(naoList.size()-1).getYear();
         result.put("start_year",naoList.get(0).getYear());
         result.put("start_month",naoList.get(0).getMonth());
         result.put("end_year",naoList.get(naoList.size()-1).getYear());
         //result.put("end_month","7");
         // 修改后 (动态获取)
         String lastMonth = naoList.get(naoList.size()-1).getMonth();
         result.put("end_month", lastMonth);

         // 处理返回数据格式
         Map<String, Object> title=new LinkedHashMap<>();
         //title.put("text",String.format("%s年%s月~%s年%s月 NAOI指数预测结果", end_year, "7", end_year, "12"));
         // 修改后 (动态月份)
         int startMonth = Integer.parseInt(lastMonth);
         int endMonthIndex = startMonth - 1 + 5;
         int endMonth = endMonthIndex % 12 + 1;
         String forecastEndYear = Integer.toString(
                 Integer.parseInt(end_year) + endMonthIndex / 12
         );
         title.put("text",String.format("%s年%s月~%s年%s月 NAOI指数预测结果", 
                 end_year, lastMonth, 
                 forecastEndYear, String.valueOf(endMonth)));
         title.put("left","center");
         naoMap.put("title",title);
         Map<String, Object> tooltip=new LinkedHashMap<>();
         naoMap.put("tooltip",tooltip);
         Map<String, Object> xAxis=new LinkedHashMap<>();
         xAxis.put("type","category");
         xAxis.put("name","时间");
         List<String> month_data=new ArrayList<>();
         String[] chinese_month = {"一月","二月","三月","四月","五月","六月","七月","八月","九月","十月","十一月","十二月"};
         int temp = startMonth;
         for(int i=0; i<6; i++) {
             month_data.add(chinese_month[temp - 1]);
             temp++;
             if(temp > 12){
                 temp = temp - 12;
             }
         }
         xAxis.put("data",month_data);
         naoMap.put("xAxis",xAxis);
         Map<String,Object> yAxis=new LinkedHashMap<>();
         yAxis.put("name","冬季NAOI");
         yAxis.put("nameLocation","center");
         Map<String,Object> nameTextStyle=new LinkedHashMap<>();
         nameTextStyle.put("frontSize",16);
         int[] padding=new int[4];
         padding[0]=0;
         padding[1]=0;
         padding[2]=15;
         padding[3]=0;
         nameTextStyle.put("padding",padding);
         String p="nameTextStyle";
         yAxis.put(p,nameTextStyle);
         yAxis.put("type","value");
         naoMap.put("yAxis",yAxis);
         Map<String,Object> legend=new LinkedHashMap<>();
         String []legend_data= new String[2];
         legend_data[0]="观测值";
         legend_data[1]="NAO-MCR";
         legend.put("data",legend_data);
         legend.put("orient","horizontal");
         legend.put("left","center");
         legend.put("bottom","5");
         naoMap.put("legend",legend);

         // 按最晚可查询年月查询预报数据和观测数据
         Tj_nao preResult = tj_naoservice.findPredictionByMonthAndModel(end_year,lastMonth);
         Obs_nao obsResult = obs_naoservice.findObservationByMonthAndModel(end_year,"1");
         String jsonString;
         ObjectMapper objectMapper=new ObjectMapper();
         double []pre_data = new double[0];
         List<Double> obs_data = new ArrayList<>();
         try{
             if (preResult != null && preResult.getData() != null) {
                 jsonString = preResult.getData();
                 pre_data = objectMapper.readValue(jsonString,double[].class);
             }

             for(int i = 0; i < 6; i++){
                 int monthIndex = startMonth - 1 + i;
                 String observationYear = Integer.toString(
                         Integer.parseInt(end_year) + monthIndex / 12
                 );
                 int observationMonth = monthIndex % 12;
                 Obs_nao yearlyObservation = observationYear.equals(end_year)
                         ? obsResult
                         : obs_naoservice.findObservationByMonthAndModel(observationYear,"1");

                 if (yearlyObservation == null || yearlyObservation.getData() == null) {
                     obs_data.add(null);
                     continue;
                 }

                 double[] yearlyData = objectMapper.readValue(
                         yearlyObservation.getData(),
                         double[].class
                 );
                 obs_data.add(observationMonth < yearlyData.length
                         ? yearlyData[observationMonth]
                         : null);
             }
         }catch (JsonProcessingException e){
             logger.error("Failed to parse NAO initialization data", e);
         }

         List<Map<String,Object>> series = new ArrayList<>();
         Map<String,Object> obs_Data=new LinkedHashMap<>();
         Map<String,Object> pre_Data=new LinkedHashMap<>();
         obs_Data.put("name","观测值");
         obs_Data.put("type","line");
         obs_Data.put("data",obs_data);
         series.add(obs_Data);
         pre_Data.put("name","NAO-MCR");
         pre_Data.put("type","line");
         pre_Data.put("data",pre_data);
         series.add(pre_Data);
         naoMap.put("series",series);

         result.put("option",naoMap);
         result.put("description","描述暂无");
         return result;
         
     }

    /**
     * 初始化预报结果模块图
     * @param: null;
     * @return: Map<String, Object>.
     */
    @GetMapping("/initialize/naoGrid")
    @ApiOperation(notes = "初始化预报结果折模块图，返回可查询年月", value = "初始化预报结果模块图")
     public Map<String, Object> initialNAOGrid(){
        List<Imgs> imgsList = new ArrayList<>(imgsservice.findAllByType("NAO"));
        Map<String, Object> naoMap=new LinkedHashMap<>();
        imgsList.removeIf(item -> item.getYear() == null
                || item.getMonth() == null
                || item.getData() == null
                || item.getData().isEmpty());
        imgsList.sort(Comparator
                .comparingInt((Imgs item) -> Integer.parseInt(item.getYear()))
                .thenComparingInt(item -> Integer.parseInt(item.getMonth())));
        if (imgsList.isEmpty()) {
            naoMap.put("data", Collections.emptyList());
            return naoMap;
        }

        // 返回最早查询年月和最晚查询年月
        String end_year=imgsList.get(imgsList.size()-1).getYear();
        String end_month=imgsList.get(imgsList.size()-1).getMonth();
        naoMap.put("start_year",imgsList.get(0).getYear());
        naoMap.put("start_month",imgsList.get(0).getMonth());
        naoMap.put("end_year",end_year);
        naoMap.put("end_month",end_month);

        // 按最晚查询年月查询图片地址
        String data = imgsservice.findNAOImgByMonth(end_year,end_month);
        naoMap.put("data", splitImagePaths(data));

        return naoMap;
     }

    /**
     * 初始化预测结果误差分布图
     * @param: null;
     * @return: Map<String, Object>.
     */
    @GetMapping("/initialize/naoCORR")
    @ApiOperation(notes = "初始化预测结果误差分布图，返回可查询年月", value = "初始化预测结果误差分布图")
     public Map<String, Object> initialNAOCORR(){
         List<Imgs> imgsList = new ArrayList<>(imgsservice.findAllByType("NAO_CORR"));
         Map<String, Object> naoMap=new LinkedHashMap<>();
         imgsList.removeIf(item -> item.getYear() == null
                 || item.getMonth() == null
                 || item.getData() == null
                 || item.getData().isEmpty());
         imgsList.sort(Comparator
                 .comparingInt((Imgs item) -> Integer.parseInt(item.getYear()))
                 .thenComparingInt(item -> Integer.parseInt(item.getMonth())));
         if (imgsList.isEmpty()) {
             naoMap.put("data", null);
             return naoMap;
         }

        // 返回最早查询年月和最晚查询年月
        String end_year=imgsList.get(imgsList.size()-1).getYear();
        String end_month=imgsList.get(imgsList.size()-1).getMonth();
        naoMap.put("start_year",imgsList.get(0).getYear());
        naoMap.put("start_month",imgsList.get(0).getMonth());
        naoMap.put("end_year",end_year);
        naoMap.put("end_month",end_month);

        // 按最晚查询年月查询图片地址
        String data=imgsservice.findNAOCORRImgByMonth(end_year,end_month);
        naoMap.put("data",data);

        return naoMap;
     }

    private Map<String, Object> emptyNAOPredictionResult(String description) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("option", Collections.emptyMap());
        result.put("description", description);
        return result;
    }

    private List<String> splitImagePaths(String data) {
        if (data == null || data.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> paths = new ArrayList<>();
        for (String item : data.split(",")) {
            if (!item.trim().isEmpty()) {
                paths.add(item.trim());
            }
        }
        return paths;
    }
}
