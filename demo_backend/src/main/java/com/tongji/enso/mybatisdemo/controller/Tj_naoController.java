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

        Tj_nao preResult = tj_naoservice.findPredictionByMonthAndModel(year,month);
        // 数据库中观测数据按年存储，所以查询月份都是1，查出来的是12个月份的数据
        Obs_nao obsResult = obs_naoservice.findObservationByMonthAndModel(year,"1");

        Map<String,Object> naoMap = new LinkedHashMap<>();
        // 将month从String转为int型整数
        int m = Integer.parseInt(month);

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
        double []pre_data = new double[6];
        double []obs_data = new double[6];
        // data[]用于临时存放转化后的原始观测数据
        double []data = null;

        String jsonString;
        ObjectMapper objectMapper=new ObjectMapper();

        try{
            // 将json形式的预测数据转化为一维数组
            jsonString = preResult.getData();
            pre_data = objectMapper.readValue(jsonString,double[].class);

            // 对于观测数据，需要根据查询月份是否大于7分两类情况处理
            // 首先将当前查询年份的观测数据转化为一维数组
            jsonString= obsResult.getData();
            data = objectMapper.readValue(jsonString,double[].class);

            // 如果查询月份大于7，则需要用到下一年的数据
            if(m > 7){
                int n = m - 7;  // n表示要用到下一年的前n个数据
                int i;
                int j = 13 - m;
                // 把该年从查询月份开始之后的数据放入obs_data[]中
                for(i = 0; i < j; i++){
                    obs_data[i] = data[m-1];
                    m++;
                }
                // 将年份从String转为int型整数，+1后再转回String型用于查询下一年的数据
                int y =Integer.parseInt(year);
                y++;
                String nextYear=Integer.toString(y);
                Obs_nao obsResult_nextYear = obs_naoservice.findObservationByMonthAndModel(nextYear,"1");
                // 将下一年的观测数据转化为一维数组
                jsonString = obsResult_nextYear.getData();
                data = objectMapper.readValue(jsonString,double[].class);

                // 把下一年前n月的数据放入obs_data[]中
                for(j = 0; j < n; j++){
                    obs_data[i] = data[j];
                    i++;
                }
            }
            // 如果查询月份小于等于7，则直接从该月份开始将数据逐个放入obs_data[]中
            else{
                for(int i = 0; i < 6; i++){
                    obs_data[i] = data[m-1];
                    m++;
                }
            }
        }catch (JsonProcessingException e){
            e.printStackTrace();
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
         int index = 0;
         List<String> naoList=new ArrayList<>();

         for(int i = 0; i<data.length();i++){
             char c = data.charAt(i);
             // 已逗号为分隔符分割图片地址
             if(c == ','){
                 naoList.add(data.substring(index,i));
                 index = i + 1;
             }
         }
         naoList.add(data.substring(index));

         return naoList;
     }

    /**
     * 初始化预报结果折线图
     * 因为观测数据是以年为单位存储的，而每次要返回六个月的数据，所以最晚可查询月份一定是12-5=7月
     * @param: null;
     * @return: Map<String, Object>.
     */
     @GetMapping("/initialize/naoPrediction")

     @ApiOperation(notes = "初始化预报结果折线图，返回可查询年月", value = "初始化预报结果折线图")
     public Map<String, Object> initialNAOPrediction() {

         List<Tj_nao> naoList =
                 tj_naoservice.findNAOByModel(
                         "index_NAO_MCD"
                 );

         Set<String> availableMonths =
                 new HashSet<>();

         if (naoList != null) {

             for (Tj_nao item : naoList) {

                 try {

                     int year =
                             Integer.parseInt(
                                     item.getYear()
                             );

                     int month =
                             Integer.parseInt(
                                     item.getMonth()
                             );

                     if (month >= 1 && month <= 12) {

                         availableMonths.add(
                                 String.format(
                                         "%04d-%02d",
                                         year,
                                         month
                                 )
                         );
                     }

                 } catch (Exception ignored) {
                 }
             }
         }


         List<String> sortedMonths =
                 new ArrayList<>(
                         availableMonths
                 );

         Collections.sort(
                 sortedMonths
         );


         Map<String, Object> result =
                 new LinkedHashMap<>();

         result.put(
                 "availableMonths",
                 sortedMonths
         );


         if (!sortedMonths.isEmpty()) {

             String start =
                     sortedMonths.get(0);

             String end =
                     sortedMonths.get(
                             sortedMonths.size() - 1
                     );

             result.put(
                     "start",
                     start
             );

             result.put(
                     "end",
                     end
             );

         } else {

             result.put(
                     "start",
                     null
             );

             result.put(
                     "end",
                     null
             );
         }


         return result;
     }

    /**
     * 初始化预报结果模块图
     * @param: null;
     * @return: Map<String, Object>.
     */
    @GetMapping("/initialize/naoGrid")
    @ApiOperation(notes = "初始化预报结果折模块图，返回可查询年月", value = "初始化预报结果模块图")
    public Map<String, Object> initialNAOGrid() {

        List<Imgs> imgsList =
                imgsservice.findAllByType(
                        "NAO"
                );


        Set<String> availableMonths =
                new HashSet<>();


        if (imgsList != null) {

            for (Imgs item : imgsList) {

                try {

                    int year =
                            Integer.parseInt(
                                    item.getYear()
                            );

                    int month =
                            Integer.parseInt(
                                    item.getMonth()
                            );

                    if (month >= 1 && month <= 12) {

                        availableMonths.add(
                                String.format(
                                        "%04d-%02d",
                                        year,
                                        month
                                )
                        );
                    }

                } catch (Exception ignored) {
                }
            }
        }


        List<String> sortedMonths =
                new ArrayList<>(
                        availableMonths
                );

        Collections.sort(
                sortedMonths
        );


        Map<String, Object> result =
                new LinkedHashMap<>();


        result.put(
                "availableMonths",
                sortedMonths
        );


        if (!sortedMonths.isEmpty()) {

            result.put(
                    "start",
                    sortedMonths.get(0)
            );

            result.put(
                    "end",
                    sortedMonths.get(
                            sortedMonths.size() - 1
                    )
            );

        } else {

            result.put(
                    "start",
                    null
            );

            result.put(
                    "end",
                    null
            );
        }


        return result;
    }

    /**
     * 初始化预测结果误差分布图
     * @param: null;
     * @return: Map<String, Object>.
     */
    @GetMapping("/initialize/naoCORR")
    @ApiOperation(notes = "初始化预测结果误差分布图，返回可查询年月", value = "初始化预测结果误差分布图")
     public Map<String, Object> initialNAOCORR(){
         List<Imgs> imgsList = imgsservice.findAllByType("NAO_CORR");
         Map<String, Object> naoMap=new HashMap<>();

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
}
