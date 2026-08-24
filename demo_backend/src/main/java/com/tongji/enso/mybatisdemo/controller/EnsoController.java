package com.tongji.enso.mybatisdemo.controller;

import com.tongji.enso.mybatisdemo.entity.online.Imgs;
import com.tongji.enso.mybatisdemo.entity.online.Tj_enso;
import com.tongji.enso.mybatisdemo.mapper.online.EnsoMapper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.time.Year;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;


import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

// 控制层：与前端进行交互

@RestController
@RequestMapping("/enso")
public class EnsoController {
    @Autowired
    private EnsoMapper ensoMapper;

    /**
     * For Niño3.4指数预测结果
     * 从 tj_enso 表中查询指定年、月的 Nino34（即 ENSO 指数） 数据（五种模型）
     *某个月有哪些模型数据，就显示哪些模型。
     * @param year
     * @param month
     * @return
     */
    @GetMapping("/predictionResult/linechart")
    public Map<String, Object> getLineChartData(
            @RequestParam("year") String year,
            @RequestParam("month") String month) {

        Gson gson = new Gson();

        Type listType =
                new TypeToken<List<Double>>() {}.getType();

        Map<String, Object> resultMap =
                new HashMap<>();


        // =========================================================
        // 1. 查询四种预测数据
        // =========================================================

        String ascJson =
                ensoMapper.findEachPredictionsResultByMonthType(
                        year,
                        month,
                        "nino34_asc"
                );

        String gtcJson =
                ensoMapper.findEachPredictionsResultByMonthType(
                        year,
                        month,
                        "nino34_gtc"
                );

        String mcJson =
                ensoMapper.findEachPredictionsResultByMonthType(
                        year,
                        month,
                        "nino34_mc"
                );

        String meanJson =
                ensoMapper.findEachPredictionsResultByMonthType(
                        year,
                        month,
                        "nino34_mean"
                );


        // =========================================================
        // 2. JSON 转 List
        //
        // 数据不存在时使用空列表，
        // 避免 NullPointerException
        // =========================================================

        List<Double> ascData =
                isBlank(ascJson)
                        ? new ArrayList<>()
                        : gson.fromJson(ascJson, listType);

        List<Double> gtcData =
                isBlank(gtcJson)
                        ? new ArrayList<>()
                        : gson.fromJson(gtcJson, listType);

        List<Double> mcData =
                isBlank(mcJson)
                        ? new ArrayList<>()
                        : gson.fromJson(mcJson, listType);

        List<Double> meanData =
                isBlank(meanJson)
                        ? new ArrayList<>()
                        : gson.fromJson(meanJson, listType);


        // 防止 JSON 内容本身是 null
        if (ascData == null) {
            ascData = new ArrayList<>();
        }

        if (gtcData == null) {
            gtcData = new ArrayList<>();
        }

        if (mcData == null) {
            mcData = new ArrayList<>();
        }

        if (meanData == null) {
            meanData = new ArrayList<>();
        }


        // =========================================================
        // 3. 如果数据库没有 mean，
        //    且 ASC、GTC、MC 三种数据全部存在，
        //    临时计算 mean
        //
        // 当前阶段只用于网页显示，
        // 暂时不写数据库。
        // =========================================================

        if (meanData.isEmpty()
                && !ascData.isEmpty()
                && !gtcData.isEmpty()
                && !mcData.isEmpty()) {

            int size =
                    Math.min(
                            ascData.size(),
                            Math.min(
                                    gtcData.size(),
                                    mcData.size()
                            )
                    );

            for (int i = 0; i < size; i++) {

                double mean =
                        (
                                ascData.get(i)
                                        + gtcData.get(i)
                                        + mcData.get(i)
                        ) / 3.0;

                meanData.add(mean);
            }
        }


        // =========================================================
        // 4. 标题
        // =========================================================

        Map<String, Object> title =
                new HashMap<>();

        title.put(
                "text",
                "Niño 3.4 Forecast Result "
                        + year
                        + "-"
                        + month
        );

        title.put(
                "left",
                "center"
        );

        resultMap.put(
                "title",
                title
        );


        // =========================================================
        // 5. tooltip
        // =========================================================

        Map<String, Object> tooltip =
                new HashMap<>();

        tooltip.put(
                "trigger",
                "axis"
        );

        resultMap.put(
                "tooltip",
                tooltip
        );


        // =========================================================
        // 6. X 轴
        //
        // 这里暂时保留项目原来的 12 个月生成逻辑，
        // 日期方向问题下一步再单独处理。
        // =========================================================

        List<String> xAxisData =
                new ArrayList<>();

        int currentMonth =
                Integer.parseInt(month);

        int currentYear =
                Integer.parseInt(year);

        int cMonth;

        if (currentMonth - 12 == 0) {
            cMonth = currentMonth;
        } else {
            cMonth = 12 + currentMonth;
        }

        int xYear;

        if (currentMonth == 12) {
            xYear = currentYear;
        } else {
            xYear = currentYear - 1;
        }

        int xMonth =
                cMonth - 11;

        for (int j = 11; j >= 0; j--) {

            if (xMonth < 13) {

                xAxisData.add(
                        xMonth + "-" + xYear
                );

                xMonth++;

            } else {

                xMonth = 1;
                xYear++;

                xAxisData.add(
                        xMonth + "-" + xYear
                );

                xMonth++;
            }
        }


        Map<String, Object> xAxis =
                new HashMap<>();

        xAxis.put(
                "type",
                "category"
        );

        xAxis.put(
                "name",
                "时间"
        );

        xAxis.put(
                "data",
                xAxisData
        );

        Map<String, Object> axisLabel =
                new HashMap<>();

        axisLabel.put(
                "interval",
                2
        );

        xAxis.put(
                "axisLabel",
                axisLabel
        );

        resultMap.put(
                "xAxis",
                xAxis
        );


        // =========================================================
        // 7. Y 轴
        // =========================================================

        Map<String, Object> yAxis =
                new HashMap<>();

        yAxis.put(
                "type",
                "value"
        );

        resultMap.put(
                "yAxis",
                yAxis
        );


        // =========================================================
        // 8. 动态创建折线
        //
        // 哪个模型有数据才创建哪个 series。
        // =========================================================

        List<Map<String, Object>> series =
                new ArrayList<>();

        List<String> legendData =
                new ArrayList<>();


        // ---------------- ASC ----------------

        if (!ascData.isEmpty()) {

            Map<String, Object> ascSeries =
                    new HashMap<>();

            ascSeries.put(
                    "name",
                    "ENSO-ASC"
            );

            ascSeries.put(
                    "type",
                    "line"
            );

            ascSeries.put(
                    "data",
                    ascData
            );

            series.add(
                    ascSeries
            );

            legendData.add(
                    "ENSO-ASC"
            );
        }


        // ---------------- GTC ----------------

        if (!gtcData.isEmpty()) {

            Map<String, Object> gtcSeries =
                    new HashMap<>();

            gtcSeries.put(
                    "name",
                    "ENSO-GTC"
            );

            gtcSeries.put(
                    "type",
                    "line"
            );

            gtcSeries.put(
                    "data",
                    gtcData
            );

            series.add(
                    gtcSeries
            );

            legendData.add(
                    "ENSO-GTC"
            );
        }


        // ---------------- MC ----------------

        if (!mcData.isEmpty()) {

            Map<String, Object> mcSeries =
                    new HashMap<>();

            mcSeries.put(
                    "name",
                    "ENSO-MC"
            );

            mcSeries.put(
                    "type",
                    "line"
            );

            mcSeries.put(
                    "data",
                    mcData
            );

            series.add(
                    mcSeries
            );

            legendData.add(
                    "ENSO-MC"
            );
        }


        // ---------------- MEAN ----------------

        if (!meanData.isEmpty()) {

            Map<String, Object> meanSeries =
                    new HashMap<>();

            meanSeries.put(
                    "name",
                    "ENSO-MEAN"
            );

            meanSeries.put(
                    "type",
                    "line"
            );

            meanSeries.put(
                    "data",
                    meanData
            );

            series.add(
                    meanSeries
            );

            legendData.add(
                    "ENSO-MEAN"
            );
        }


        // =========================================================
        // 9. legend
        // =========================================================

        Map<String, Object> legend =
                new HashMap<>();

        legend.put(
                "data",
                legendData
        );

        legend.put(
                "orient",
                "horizontal"
        );

        legend.put(
                "left",
                "center"
        );

        legend.put(
                "bottom",
                "5"
        );

        resultMap.put(
                "legend",
                legend
        );


        // =========================================================
        // 10. series
        // =========================================================

        resultMap.put(
                "series",
                series
        );

        return resultMap;
    }


    @GetMapping("/predictionExamination/monthlyComparison")
    public Map<String, Object> getMonthlyComparison(@RequestParam("year") String year, @RequestParam("month") String month) {
        Gson gson = new Gson();

        Type listType = new TypeToken<List<Object>>() {
        }.getType();
        int currentMonthStatic = Integer.parseInt(month);
        int currentYearStatic = Integer.parseInt(year);
        String lastYear = String.valueOf(currentYearStatic-1);
        List<Object> obsData=new ArrayList<>();// 用来存放obs数据

        // 查询所有数据,今年和去年
        String YearResult2022 = ensoMapper.findObsEnsoByYear(lastYear);
        if (YearResult2022 != null) {
            List<Object> currentYearData1 = gson.fromJson(YearResult2022, listType);
            obsData.addAll(currentYearData1);
        }
        String YearResult2023 = ensoMapper.findObsEnsoByYear(year);
        if (YearResult2023 != null) {
            List<Object> currentYearData2 = gson.fromJson(YearResult2023, listType);
            obsData.addAll(currentYearData2);
        }
        int CMonth;
        if(currentMonthStatic-12==0){
            CMonth = currentMonthStatic;
        }
        else{
            CMonth=12+currentMonthStatic;
        }
        List<Object> obs_data = new ArrayList<>();
        if (CMonth-12 >0)  // 最多往前查12个月
        {
            obs_data = obsData.subList(CMonth-12,CMonth);
        }
        else{
            obs_data = obsData.subList(0, CMonth);
        }

        //map
        Map<String, Object> resultMap = new HashMap<>();
        Map<String, Object> resultMap1 = new HashMap<>();
        Map<String, Object> title = new HashMap<>();
        Map<String, Object> tooltip = new HashMap<>();
        Map<String, Object> legend = new HashMap<>();
        Map<String, Object> legend_tooltip = new HashMap<>();
        Map<String, Object> grid = new HashMap<>();
        Map<String, Object> toolbox = new HashMap<>();
        Map<String, Object> toolbox_feature = new HashMap<>();
        Map<String, Object> toolbox_feature_saveAsImage = new HashMap<>();
        Map<String, Object> xAxis = new HashMap<>();
        List<Object> xAxis_data = new ArrayList<>();
        Map<String, Object> xAxis_axisLabel = new HashMap<>();
        Map<String, Object> yAxis = new HashMap<>();
        List<Map<String, Object>> series= new ArrayList<>();
        Map<String, Object> series_title = new HashMap<>();
        Map<String, Object> series_title_lineStyle = new HashMap<>();
        Map<String, Object> series_title_itemStyle = new HashMap<>();

        String title_text="预测结果逐月对比";
        String title_left="center";
        String tooltip_trigger="item";
        String legend_x="center";
        String legend_y="bottom";
        String legend_tooltip_show="true";
        String grid_left="3%";
        String grid_right="4%";
        String grid_bottom="25%";
        String grid_containLabel="true";
        String xAxis_type="category";
        String xAxis_boundaryGap="false";
        int currentYear1;
        if(currentMonthStatic==12){
            currentYear1=Integer.parseInt(year);
        }
        else{
            currentYear1=Integer.parseInt(year)-1;
        }
        int currentMonth1=CMonth-11;
        //xAxis处理
        for(int j=11;j>=0;j--){

            if(currentMonth1<13){
                xAxis_data.add(currentMonth1+"-"+currentYear1);
                currentMonth1++;
            }
            else{
                currentMonth1=1;
                currentYear1+=1;
                xAxis_data.add(currentMonth1+"-"+currentYear1);
                currentMonth1++;
            }

        }
        int xAxis_interval=2;
        String yAxis_type="value";
        String yAxis_name="Niño 3.4 Index";
        String series_title_name="气候中心Nino3.4指数记录";
        String series_title_type="line";
        String series_title_lineStyle_color="black";
        int series_title_lineStyle_width=3;
        String series_title_itemStyle_color="black";
        String resultmap1_text="此处为预测结果汇总折线图。除22年10月-23年1月外，其他月份的预测结果与官方记录差异不大。预测误差较大可能与近年来连续冷（拉尼娜）事件快结束时状态不稳定有关。\n";

        resultMap1.put("text",resultmap1_text);
        title.put("text", title_text);
        title.put("left", title_left);
        tooltip.put("trigger", tooltip_trigger);
        legend.put("x", legend_x);
        legend.put("y", legend_y);
        legend.put("tooltip", legend_tooltip);
        legend_tooltip.put("show",legend_tooltip_show);
        grid.put("left",grid_left);
        grid.put("right",grid_right);
        grid.put("bottom",grid_bottom);
        grid.put("containLabel",grid_containLabel);
        toolbox.put("feature",toolbox_feature);
        toolbox_feature.put("saveAsImage",toolbox_feature_saveAsImage);
        xAxis.put("type",xAxis_type);
        xAxis.put("boundaryGap",xAxis_boundaryGap);
        xAxis.put("data",xAxis_data);
        xAxis.put("axisLabel",xAxis_axisLabel);
        xAxis_axisLabel.put("interval",xAxis_interval);
        yAxis.put("type",yAxis_type);
        yAxis.put("name",yAxis_name);
        series_title.put("name",series_title_name);
        series_title.put("type",series_title_type);
        series_title.put("lineStyle",series_title_lineStyle);
        series_title.put("itemStyle",series_title_itemStyle);
        series_title_lineStyle.put("color",series_title_lineStyle_color);
        series_title_lineStyle.put("width",series_title_lineStyle_width);
        series_title_itemStyle.put("color",series_title_itemStyle_color);
        series_title.put("data",obs_data);
        series.add(series_title);

        int currentYear,currentMonth;
        // 预测数据
        for (int i = 11; i >= 0; i--) {
            if (currentMonthStatic - i < 1) {
                currentYear = currentYearStatic - 1;
                currentMonth = currentMonthStatic + 12 - i;
            } else {
                currentYear = currentYearStatic;
                currentMonth = currentMonthStatic - i;
            }
            String Year = String.valueOf(currentYear);
            String Month = String.valueOf(currentMonth);
            String predictionMeanResult = ensoMapper.findEachPredictionsResultByMonthType(Year, Month, "nino34_mean");
            if (predictionMeanResult != null) {
                Map<String, Object> temp = new HashMap<>();
                String temp_name = Year + "年" + Month + "月起报预报误差";
                String temp_type = "line";
                String temp_lineStyle_type = "dashed";
                Map<String, Object> temp_lineStyle = new HashMap<>();

                temp.put("name", temp_name);
                temp.put("type", temp_type);
                temp.put("lineStyle", temp_lineStyle);
                temp_lineStyle.put("lineStyle", temp_lineStyle_type);
                if (predictionMeanResult != null) {
                    List<Object> predictionMeanList = gson.fromJson(predictionMeanResult, listType);  // 将结果字符串转换为列表
                    List<Object> predictionMeanData = predictionMeanList.subList(0, i + 1);
                    for (int j = 11; j > i; j--) {
                        predictionMeanData.add(0, "null");
                    }

                    temp.put("data", predictionMeanData);
                }
                series.add(temp);
            }
        }
        //最后的总和
        resultMap.put("title",title);
        resultMap.put("tooltip",tooltip);
        resultMap.put("legend",legend);
        resultMap.put("grid",grid);
        resultMap.put("toolbox",toolbox);
        resultMap.put("xAxis",xAxis);
        resultMap.put("yAxis",yAxis);
        resultMap.put("series",series);
        resultMap1.put("option",resultMap);
        return resultMap1;

    }


    /**
     * 计算预测模型 nino34_mean 与观测数据在指定年月的绝对误差
     *
     * @param year
     * @param month
     * @return
     */
    @GetMapping("/predictionExamination/error")
    public Map<String, Object> getError(@RequestParam("year") String year, @RequestParam("month") String month) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<Double>>() {
        }.getType();
        int currentMonthStatic = Integer.parseInt(month);
        int currentYearStatic = Integer.parseInt(year);
        String lastYear = String.valueOf(currentYearStatic-1);

        List<Object> obsData=new ArrayList<>();// 用来存放obs数据

        // 查询所有数据,今年和去年
        String YearResult2022 = ensoMapper.findObsEnsoByYear(lastYear);
        if (YearResult2022 != null) {
            List<Object> currentYearData1 = gson.fromJson(YearResult2022, listType);
            obsData.addAll(currentYearData1);
        }
        String YearResult2023 = ensoMapper.findObsEnsoByYear(year);
        if (YearResult2023 != null) {
            List<Object> currentYearData2 = gson.fromJson(YearResult2023, listType);
            obsData.addAll(currentYearData2);
        }
        int CMonth;
        if(currentMonthStatic-12==0){
            CMonth = currentMonthStatic;
        }
        else{
            CMonth=12+currentMonthStatic;
        }
        List<Object> obs_data = new ArrayList<>();
        if (CMonth-12 >0)  // 最多往前查12个月
        {
            obs_data = obsData.subList(CMonth-12,CMonth);
        }
        else{
            obs_data = obsData.subList(0, CMonth);
        }
        List<Object> option=new ArrayList<>();// 用来存放obs数据
        int currentYear,currentMonth;
        // 预测数据
        for (int i = 11; i >= 0; i--) {
            //全部放进去
            //map

            Map<String, Object> resultMap = new HashMap<>();
            Map<String, Object> title = new HashMap<>();
            Map<String, Object> tooltip = new HashMap<>();
            Map<String, Object> tooltip_axisPointer = new HashMap<>();
            Map<String, Object> tooltip_axisPointer_crossStyle = new HashMap<>();
            Map<String, Object> legend = new HashMap<>();
            Map<String, Object> legend_tooltip = new HashMap<>();
            Map<String, Object> toolbox = new HashMap<>();
            Map<String, Object> toolbox_feature = new HashMap<>();
            Map<String, Object> toolbox_feature_saveAsImage = new HashMap<>();
            Map<String,Object> xAxis= new HashMap<>();
            List<Object> xAxis1 = new ArrayList<>();
            List<Object> xAxis_data = new ArrayList<>();
            Map<String, Object> xAxis_axisLabel = new HashMap<>();
            Map<String, Object> xAxis_axisPointer = new HashMap<>();
            Map<String,Object> yAxis= new HashMap<>();
            List<Object> yAxis1 = new ArrayList<>();
            List<Map<String, Object>> series= new ArrayList<>();

            String title_text="预报结果误差柱状图";
            String title_left="center";
            String tooltip_trigger="axis";
            String tooltip_axisPointer_type="cross";
            String tooltip_axisPointer_crossStyle_color="#999";
            String legend_x="center";
            String legend_y="bottom";
            String legend_tooltip_show="true";
            String xAxis_type="category";
            String xAxis_axisPointer_type="shadow";
            int xAxis_interval=1;
            String yAxis_type="value";
            String yAxis_name="Niño 3.4 Index";

            int currentYear1;
            if(currentMonthStatic==12){
                currentYear1=Integer.parseInt(year);
            }
            else{
                currentYear1=Integer.parseInt(year)-1;
            }
            int currentMonth1=CMonth-11;
            //xAxis处理
            for(int j=11;j>=0;j--){

                if(currentMonth1<13){
                    xAxis_data.add(currentYear1+"-"+currentMonth1);
                    currentMonth1++;
                }
                else{
                    currentMonth1=1;
                    currentYear1+=1;
                    xAxis_data.add(currentYear1+"-"+currentMonth1);
                    currentMonth1++;
                }

            }

            //预测数据部分
            if (currentMonthStatic - i < 1) {
                currentYear = currentYearStatic - 1;
                currentMonth = currentMonthStatic + 12 - i;
            } else {
                currentYear = currentYearStatic;
                currentMonth = currentMonthStatic - i;
            }
            String Year = String.valueOf(currentYear);
            String Month = String.valueOf(currentMonth);
            String predictionMeanResult = ensoMapper.findEachPredictionsResultByMonthType(Year, Month, "nino34_mean");
            if (predictionMeanResult != null) {
                Map<String, Object> temp = new HashMap<>();
                Map<String, Object> tempObs = new HashMap<>();
                Map<String, Object> tempError = new HashMap<>();

                title.put("text", title_text);
                title.put("left", title_left);
                tooltip.put("trigger", tooltip_trigger);
                tooltip.put("axisPointer", tooltip_axisPointer);
                tooltip_axisPointer.put("type", tooltip_axisPointer_type);
                tooltip_axisPointer.put("crossStyle", tooltip_axisPointer_crossStyle);
                tooltip_axisPointer_crossStyle.put("color", tooltip_axisPointer_crossStyle_color);
                legend.put("x", legend_x);
                legend.put("y", legend_y);
                legend.put("tooltip", legend_tooltip);
                legend_tooltip.put("show", legend_tooltip_show);
                toolbox.put("feature", toolbox_feature);
                toolbox_feature.put("saveAsImage", toolbox_feature_saveAsImage);
                xAxis.put("type", xAxis_type);
                xAxis.put("axisPointer", xAxis_axisPointer);
                xAxis_axisPointer.put("type", xAxis_axisPointer_type);
                int remove_len = 11 - i;
                for (;remove_len>0;remove_len--){
                    xAxis_data.remove(0);//每次删去一个
                }
                xAxis.put("data", xAxis_data);
                xAxis.put("axisLabel", xAxis_axisLabel);
                if(i<7){
                    xAxis_axisLabel.put("interval", xAxis_interval+1);
                }
                else{
                    xAxis_axisLabel.put("interval", xAxis_interval);
                }
                yAxis.put("type", yAxis_type);
                yAxis.put("name", yAxis_name);

                //误差

                String temp_name = Year + "年" + Month + "月起报";
                String temp_type = "line";

                temp.put("name", temp_name);
                temp.put("type", temp_type);
                List<Object> predictionMeanData = new ArrayList<>();

                List<Object> predictionMeanList = gson.fromJson(predictionMeanResult, listType);  // 将结果字符串转换为列表
                predictionMeanData = predictionMeanList.subList(0, i + 1);
                ;
                temp.put("data", predictionMeanData);

                series.add(temp);
                //标准
                Map<String, Object> tempObs_lineStyle = new HashMap<>();
                List<Object> obs_data_fin = obs_data.subList(11 - i, obs_data.size());
                String tempObs_name = "气候中心Nino3.4指数记录";
                String tempObs_type = "line";
                String tempObs_lineStyle_type = "dashed";
                tempObs_lineStyle.put("type", tempObs_lineStyle_type);
                tempObs.put("name", tempObs_name);
                tempObs.put("type", tempObs_type);
                tempObs.put("lineStyle", tempObs_lineStyle);
                tempObs.put("data", obs_data_fin);
                series.add(tempObs);
                //差集
                List<Object> error = new ArrayList<>();
                for (int j = 0; j <= i; j++) {
                    //？？？
                    error.add(Math.abs((Double.parseDouble(predictionMeanData.get(j).toString()) - Double.parseDouble(obs_data_fin.get(j).toString()))));
                }
                String tempError_name = Year + "年" + Month + "月起报误差";
                tempError.put("name", tempError_name);
                tempError.put("type", "bar");
                tempError.put("lineStyle", tempObs_lineStyle);
                tempError.put("data", error);
                series.add(tempError);

                //最后的总和
                resultMap.put("title",title);
                resultMap.put("tooltip",tooltip);
                resultMap.put("legend",legend);
                resultMap.put("toolbox",toolbox);
                xAxis1.add(xAxis);
                yAxis1.add(yAxis);
                resultMap.put("xAxis",xAxis1);
                resultMap.put("yAxis",yAxis1);
                resultMap.put("series",series);
                option.add(resultMap);
            }

        }
        Map <String,Object>FinResult=new HashMap<>();
        String text="此处的12副图分别为从2022年2月~2023年1月起报的预测结果、官方记录结果及二者绝对差值图（柱状）。\n";
        FinResult.put("option",option);
        FinResult.put("text",text);
        return FinResult;
    }

    /**
     * 返回列表数据的中位数
     *
     * @param data
     * @return
     */
    private double getMedian(List<Double> data) {
        int size = data.size();
        // 打印 size
        System.out.println("size: " + size);
        if (size % 2 == 0) {
            return (data.get(size / 2 - 1) + data.get(size / 2)) / 2.0;
        } else {
            return data.get(size / 2);
        }
    }

    private double calculateQuartile(List<Double> sortedData, int quartileNumber) {
        int dataSize = sortedData.size();
        int index = (int) Math.ceil(quartileNumber * 0.25 * (dataSize + 1)) - 1;
        if (dataSize % 4 == 0) {
            return (sortedData.get(index) + sortedData.get(index + 1)) / 2.0;
        } else {
            return sortedData.get(index);
        }
    }

    /**
     * 计算数据：最小值、下四分位数(Q1)、中位数、上四分位数(Q3)、最大值五个统计量
     * 说明："- 其中[0.1, 0.2, 0.3, 0.4, 0.5] 顺序为：最小值、下四分位数、中位数、上四分位数、最大值
     * - 这里的data必须为[[ ]] （两层中括号，因为不同月份的箱用不同颜色区分）"
     *
     * 由小到大排列并分成四等份：Q1 Q2 Q3
     */
    private List<List<Double>> computeBoxPlotData(List<Double> data) {
        // 对 data 排序
        Collections.sort(data);

        double min = Collections.min(data);
        double max = Collections.max(data);
        double median = getMedian(data);

        double q1 = calculateQuartile(data, 1); // 计算下四分位数（Q1）
        double q3 = calculateQuartile(data, 3); // 计算上四分位数（Q3）

        List<List<Double>> result = new ArrayList<>();
        result.add(Arrays.asList(min, q1, median, q3, max));

        return result;
    }

    /**
     * 获取指定 year、month 的观测数据
     */
    @GetMapping("/predictionExamination/obsData")
    public List<Double> getObsData (@RequestParam("year") String year, @RequestParam("month") String month) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<Double>>() {
        }.getType();

        // 使用给定年份查询所有数据
        String currentYearResult = ensoMapper.findObsEnsoByYear(year);
        if (currentYearResult == null) {
            return null; // 该年没有观测数据，交由调用方判空跳过，避免空指针
        }
        List<Double> currentYearData = gson.fromJson(currentYearResult, listType);

        List<Double> current18MonthsData;  // 用来存放从本月起（包括本月）未来18个月的数据，直到真实数据用完为止

        current18MonthsData = currentYearData.subList(Integer.parseInt(month) - 1, currentYearData.size());

        if (currentYearData.size() == 12)  // 需要考虑下一年的真实数据
        {
            // 查询下一年的数据
            String futureYear = String.valueOf(Integer.parseInt(year) + 1);
            String futureYearResult = ensoMapper.findObsEnsoByYear(futureYear);

            if (futureYearResult != null) {
                List<Double> futureYearData = gson.fromJson(futureYearResult, listType);
                current18MonthsData.addAll(futureYearData);
                if (futureYearData.size() == 12 && current18MonthsData.size() < 18) {
                    // 查询下一年的下一年的数据
                    String futureFutureYear = String.valueOf(Integer.parseInt(year) + 2);
                    String futureFutureYearResult = ensoMapper.findObsEnsoByYear(futureFutureYear);
                    if (futureFutureYearResult != null) {
                        List<Double> futureFutureYearData = gson.fromJson(futureFutureYearResult, listType);
                        current18MonthsData.addAll(futureFutureYearData);
                    }
                }
            }
        }

        if (current18MonthsData.size() > 18)  // 超过18个月的数据，只取前18个月
            current18MonthsData = current18MonthsData.subList(0, 18);

        return current18MonthsData;
    }

    /**
     * 获取指定 year、month 的预测数据 平均
     */
    @GetMapping("/predictionExamination/predictionData")
    public List<Double> getPreData(@RequestParam("year") String year, @RequestParam("month") String month) {
        Gson gson = new Gson();
        Type listType = new TypeToken<List<Double>>() {
        }.getType();
        // 获取预测数据
        String predictionMeanResult = ensoMapper.findEachPredictionsResultByMonthType(year, month, "nino34_mean");
        List<Double> predictionMeanList = gson.fromJson(predictionMeanResult, listType);

        return predictionMeanList;
    }

    /**
     * 计算 year month 之前要查询的 年月 数据
     */
    @GetMapping("/utils/monthsToQuery")
    @ApiOperation(value = "计算 year month 之前要查询的 年月 数据")
    public List<String> calculateMonthsToQuery(int year, int month, int monthsToGoBack) {
        List<String> monthsToQuery = new ArrayList<>();
        for (int i = monthsToGoBack; i >= 1; i--) {
            int queryMonth = (month - i + 12) % 12 + 1; // 加1来保证月份范围从1到12
            int queryYear = year - ((month - i) < 0 ? 1 : 0);
            String formattedMonth = String.format("%04d-%02d", queryYear, queryMonth);
            monthsToQuery.add(formattedMonth);
        }
        return monthsToQuery;
    }


    /**
     * 预测模型 nino34_mean 预测误差箱形图绘制
     *
     * @param year
     * @param month
     * @return
     */
    @GetMapping("/predictionExamination/errorBox")
    @ApiOperation(value = "误差计算：预测 - 观测", notes = "数据库数据有限，目前只能通过 year=2023 month=2 来访问")
    public Map<String, Object> getErrorBox(@RequestParam("year") String year, @RequestParam("month") String month) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> option = new HashMap<>();
        List<Map<String, Object>> titleList = new ArrayList<>();
        Map<String, Object> title = new HashMap<>();
        title.put("text", "误差分析箱型图");
        title.put("left", "center");
        titleList.add(title);
        option.put("title", titleList);

        Map<String, Object> legend = new HashMap<>();
        legend.put("x", "center");
        legend.put("y", "bottom");
        Map<String, Object> tooltipLegend = new HashMap<>();
        tooltipLegend.put("show", true);
        legend.put("tooltip", tooltipLegend);
        option.put("legend", legend);

        Map<String, Object> tooltip = new HashMap<>();
        tooltip.put("trigger", "item");
        Map<String, Object> axisPointer = new HashMap<>();
        axisPointer.put("type", "shadow");
        tooltip.put("axisPointer", axisPointer);
        option.put("tooltip", tooltip);

        Map<String, Object> grid = new HashMap<>();
        grid.put("left", "10%");
        grid.put("right", "10%");
        grid.put("bottom", "25%");
        option.put("grid", grid);

        Map<String, Object> xAxis = new HashMap<>();
        xAxis.put("type", "category");
        xAxis.put("boundaryGap", true);
        xAxis.put("show", false);
        Map<String, Object> splitAreaX = new HashMap<>();
        splitAreaX.put("show", false);
        xAxis.put("splitArea", splitAreaX);
        Map<String, Object> splitLineX = new HashMap<>();
        splitLineX.put("show", false);
        xAxis.put("splitLine", splitLineX);
        option.put("xAxis", xAxis);

        Map<String, Object> yAxis = new HashMap<>();
        yAxis.put("type", "value");
        yAxis.put("name", "Niño 3.4 Index");
        Map<String, Object> splitAreaY = new HashMap<>();
        splitAreaY.put("show", true);
        yAxis.put("splitArea", splitAreaY);
        List<Double> yAxisData = Arrays.asList(0.0, 0.1, 0.2, 0.3, 0.4, 0.5);
        yAxis.put("data", yAxisData);
        option.put("yAxis", yAxis);

        List<Map<String, Object>> seriesList = new ArrayList<>();
        List<String> monthsToQuery = calculateMonthsToQuery(Integer.parseInt(year), Integer.parseInt(month), 12);
        for (String queryDate : monthsToQuery) {  // 从当前月份往前推12个月 （series 中包括12条数据）
            // 打印 queryDate
            // System.out.println(queryDate);
            String queryYear = queryDate.substring(0, 4);
            String queryMonth = queryDate.substring(5);
            // 如果第一位是0，去掉
            if (queryMonth.startsWith("0")) {
                queryMonth = queryMonth.substring(1); // 去掉开头的零
            }
            // System.out.println("year: " + queryYear + ", month: " + queryMonth);

            List<Double> preData = getPreData(queryYear, queryMonth);
            List<Double> obsData = getObsData(queryYear, queryMonth);

            // 该月起报的预测或观测数据缺失时，跳过该月，避免空指针导致整个接口 500
            if (preData == null || obsData == null) {
                continue;
            }

            // 两个数组可能不一样长，取短的
            int length = Math.min(preData.size(), obsData.size());
            preData = preData.subList(0, length);
            obsData = obsData.subList(0, length);
            // 计算误差
            List<Double> errorData = new ArrayList<>();
            for (int i = 0; i < length; i++) {
                errorData.add(Math.abs(preData.get(i) - obsData.get(i)));
            }
            System.out.println(errorData);
            // 计算箱型图数据
            List<List<Double>> boxPlotData = computeBoxPlotData(errorData);

            Map<String, Object> seriesItem = new HashMap<>();
            seriesItem.put("type", "boxplot");
            String name = queryYear + "年" + queryMonth + "月起报预报误差";
            seriesItem.put("name", name);
            seriesItem.put("data", boxPlotData);
            seriesList.add(seriesItem);
        }

        option.put("series", seriesList);

        result.put("option", option);
        result.put("text", "这里是说明文字");

        return result;
    }

    /**
     * 根据提供的观测值和预测值计算皮尔逊相关系数
     *
     * @param observed
     * @param predicted
     * @return
     */
    private double getPearsonCorrelation(List<Double> observed, List<Double> predicted) {
        double[] obsArray = observed.stream().mapToDouble(d -> d).toArray();
        double[] predArray = predicted.stream().mapToDouble(d -> d).toArray();

        PearsonsCorrelation correlation = new PearsonsCorrelation();
        return correlation.correlation(obsArray, predArray);
    }

    /**
     * 返回 year month 当月 mean 模型预测数据和真实数据的皮尔逊相关系数
     *
     * @param year
     * @param month
     * @return
     */
    @GetMapping("/predictionExamination/errorCorr")
    @ApiOperation(value = "皮尔逊相关系数")
    public Map<String, Object> getErrorCorr(@RequestParam("year") String year, @RequestParam("month") String month) {
        Map<String, Object> result = new HashMap<>();

        Map<String, Object> option = new HashMap<>();
        List<Map<String, Object>> title = new ArrayList<>();
        Map<String, Object> titleElement = new HashMap<>();
        titleElement.put("text", "预测结果逐月相关性分析");
        titleElement.put("left", "center");
        title.add(titleElement);
        option.put("title", title);

        Map<String, Object> xAxis = new HashMap<>();
        xAxis.put("type", "category");
        List<String> xAxisData = new ArrayList<>();

        List<String> monthsToQuery = calculateMonthsToQuery(Integer.parseInt(year), Integer.parseInt(month), 12);
        for (String queryDate : monthsToQuery)
        {
            String queryYear = queryDate.substring(0, 4);
            String queryMonth = queryDate.substring(5);
            // 如果第一位是0，去掉
            if (queryMonth.startsWith("0")) {
                queryMonth = queryMonth.substring(1); // 去掉开头的零
            }

            String name = queryYear + "年" + queryMonth + "月起报结果";
            xAxisData.add(name);
        }
        xAxis.put("data", xAxisData);
        Map<String, Object> axisLabel = new HashMap<>();
        axisLabel.put("show", true);
        axisLabel.put("rotate", 30);
        xAxis.put("axisLabel", axisLabel);
        option.put("xAxis", xAxis);

        Map<String, Object> yAxis = new HashMap<>();
        yAxis.put("type", "value");
        yAxis.put("name", "Niño 3.4 Index");
        yAxis.put("data", Arrays.asList(0, 0.2, 0.4, 0.6, 0.8, 1));
        option.put("yAxis", yAxis);

        List<Map<String, Object>> series = new ArrayList<>();
        Map<String, Object> seriesElement = new HashMap<>();

        seriesElement.put("type", "line");
        List<Double> seriesData = new ArrayList<>();
        // 计算皮尔逊相关系数
        for (String queryDate : monthsToQuery)
        {
            String queryYear = queryDate.substring(0, 4);
            String queryMonth = queryDate.substring(5);
            // 如果第一位是0，去掉
            if (queryMonth.startsWith("0")) {
                queryMonth = queryMonth.substring(1); // 去掉开头的零
            }
            // System.out.println("year: " + queryYear + ", month: " + queryMonth);

            List<Double> preData = getPreData(queryYear, queryMonth);
            List<Double> obsData = getObsData(queryYear, queryMonth);

            // 该月起报的预测或观测数据缺失时，跳过该月，避免空指针导致整个接口 500
            if (preData == null || obsData == null) {
                continue;
            }

            // 两个数组可能不一样长，取短的
            int length = Math.min(preData.size(), obsData.size());
            preData = preData.subList(0, length);
            obsData = obsData.subList(0, length);
            System.out.println(preData);
            System.out.println(obsData);
            double correlation = getPearsonCorrelation(preData, obsData);
            seriesData.add(correlation);
        }
        seriesElement.put("data", seriesData);
        series.add(seriesElement);
        option.put("series", series);

        result.put("option", option);
        result.put("text", "这里是说明文字");

        return result;
    }


    /**
     * 把 tj_enso 数据转换成 yyyy-MM 的月份集合。
     */
    private Set<String> toEnsoMonthSet(
            List<Tj_enso> data
    ) {

        Set<String> months =
                new HashSet<>();

        if (data == null) {
            return months;
        }

        for (Tj_enso item : data) {

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

                    months.add(
                            String.format(
                                    "%04d-%02d",
                                    year,
                                    month
                            )
                    );
                }

            } catch (NumberFormatException ignored) {

                // 数据库中年月格式非法时跳过
            }
        }

        return months;
    }
    /**
     * 初始化：返回可选年、月范围 linechart
     */
    @GetMapping("/linechart/getInitData")
    @ApiOperation(value = "初始化：返回可选年、月范围 linechart", notes = "返回的是可查询年月")
    public Map<String, Object> getLinechartInitMonth() {

        List<Tj_enso> ascData =
                ensoMapper.findTj_ensoInfoByType("nino34_asc");

        List<Tj_enso> gtcData =
                ensoMapper.findTj_ensoInfoByType("nino34_gtc");

        List<Tj_enso> mcData =
                ensoMapper.findTj_ensoInfoByType("nino34_mc");

        List<Tj_enso> meanData =
                ensoMapper.findTj_ensoInfoByType("nino34_mean");


        // 使用 Set 自动去重
        Set<String> availableMonths =
                new HashSet<>();

        // 求并集：任意模型有数据，这个月就可用
        availableMonths.addAll(
                toEnsoMonthSet(ascData)
        );

        availableMonths.addAll(
                toEnsoMonthSet(gtcData)
        );

        availableMonths.addAll(
                toEnsoMonthSet(mcData)
        );

        availableMonths.addAll(
                toEnsoMonthSet(meanData)
        );


        // yyyy-MM 格式可以直接按字符串排序
        List<String> sortedMonths =
                new ArrayList<>(availableMonths);

        Collections.sort(sortedMonths);


        Map<String, Object> result =
                new HashMap<>();

        // 新增：真正可用的月份列表
        result.put(
                "availableMonths",
                sortedMonths
        );


        // 暂时保留旧字段，兼容现有前端
        if (!sortedMonths.isEmpty()) {

            result.put(
                    "earliestDate",
                    sortedMonths.get(0)
            );

            result.put(
                    "latestDate",
                    sortedMonths.get(
                            sortedMonths.size() - 1
                    )
            );

        } else {

            result.put(
                    "earliestDate",
                    null
            );

            result.put(
                    "latestDate",
                    null
            );
        }

        return result;
    }

    /**
     * 初始化：返回可选年、月范围 errorBox
     */
    @GetMapping("/errorBox/getInitData")
    @ApiOperation(value = "初始化：返回可选年、月范围 errorBox", notes = "返回的是可查询年月")
    public Map<String, Object> getErrorBoxInitMonth()
    {
        List<Tj_enso> ensoData = ensoMapper.findTj_ensoInfoByType("nino34_mean");

        String earliestDate = null;
        String latestDate = null;

        for (Tj_enso enso : ensoData) {
            int year = Integer.parseInt(enso.getYear());
            int month = Integer.parseInt(enso.getMonth());
            if (earliestDate == null || year < Integer.parseInt(earliestDate.split("-")[0]) || (year == Integer.parseInt(earliestDate.split("-")[0]) && month < Integer.parseInt(earliestDate.split("-")[1]))) {
                earliestDate = year + "-" + month;
            }
            if (latestDate == null || year > Integer.parseInt(latestDate.split("-")[0]) || (year == Integer.parseInt(latestDate.split("-")[0]) && month > Integer.parseInt(latestDate.split("-")[1]))) {
                latestDate = year + "-" + month;
            }
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-M");  // YearMonth 无法直接解析形如 xxxx-x

        YearMonth latestYearMonth = YearMonth.parse(latestDate, dateFormatter);
        YearMonth earliestYearMonth = YearMonth.parse(earliestDate, dateFormatter);
        earliestYearMonth = earliestYearMonth.plusMonths(11);  // 因为每次查询前11个月的数据
        latestYearMonth = latestYearMonth.minusMonths(2);  // 因为要计算四分位，至少要4个数据

        Map<String, Object> result = new HashMap<>();
        result.put("earliestDate", earliestYearMonth);
        result.put("latestDate", latestYearMonth);
        return result;
    }

    /**
     * 初始化：返回可选年、月范围 errorCorr
     */
    @GetMapping("/errorCorr/getInitData")
    @ApiOperation(value = "初始化：返回可选年、月范围 errorCorr", notes = "返回的是可查询年月")
    public Map<String, Object> getErrorCorrInitMonth()
    {
        List<Tj_enso> ensoData = ensoMapper.findTj_ensoInfoByType("nino34_mean");

        String earliestDate = null;
        String latestDate = null;

        for (Tj_enso enso : ensoData) {
            int year = Integer.parseInt(enso.getYear());
            int month = Integer.parseInt(enso.getMonth());
            if (earliestDate == null || year < Integer.parseInt(earliestDate.split("-")[0]) || (year == Integer.parseInt(earliestDate.split("-")[0]) && month < Integer.parseInt(earliestDate.split("-")[1]))) {
                earliestDate = year + "-" + month;
            }
            if (latestDate == null || year > Integer.parseInt(latestDate.split("-")[0]) || (year == Integer.parseInt(latestDate.split("-")[0]) && month > Integer.parseInt(latestDate.split("-")[1]))) {
                latestDate = year + "-" + month;
            }
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-M");  // YearMonth 无法直接解析形如 xxxx-x

        YearMonth latestYearMonth = YearMonth.parse(latestDate, dateFormatter);
        YearMonth earliestYearMonth = YearMonth.parse(earliestDate, dateFormatter);
        earliestYearMonth = earliestYearMonth.plusMonths(11);  // 因为每次查询前11个月的数据

        Map<String, Object> result = new HashMap<>();
        result.put("earliestDate", earliestYearMonth);
        result.put("latestDate", latestYearMonth);
        return result;
    }

    /**
     * 初始化：返回可选年、月范围 monthlyComparison
     */
    @GetMapping("/monthlyComparison/getInitData")
    @ApiOperation(value = "初始化：返回可选年、月范围 monthlyComparison", notes = "返回的是可查询年月")
    public Map<String, Object> getmonthlyComparisonInitMonth()
    {
        List<Tj_enso> ensoData = ensoMapper.findTj_ensoInfoByType("nino34_mean");

        String earliestDate = null;
        String latestDate = null;

        for (Tj_enso enso : ensoData) {
            int year = Integer.parseInt(enso.getYear());
            int month = Integer.parseInt(enso.getMonth());
            if (earliestDate == null || year < Integer.parseInt(earliestDate.split("-")[0]) || (year == Integer.parseInt(earliestDate.split("-")[0]) && month < Integer.parseInt(earliestDate.split("-")[1]))) {
                earliestDate = year + "-" + month;
            }
            if (latestDate == null || year > Integer.parseInt(latestDate.split("-")[0]) || (year == Integer.parseInt(latestDate.split("-")[0]) && month > Integer.parseInt(latestDate.split("-")[1]))) {
                latestDate = year + "-" + month;
            }
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-M");  // YearMonth 无法直接解析形如 xxxx-x

        YearMonth latestYearMonth = YearMonth.parse(latestDate, dateFormatter);
        YearMonth earliestYearMonth = YearMonth.parse(earliestDate, dateFormatter);
        earliestYearMonth = earliestYearMonth.plusMonths(11);  // 因为每次查询前11个月的数据

        Map<String, Object> result = new HashMap<>();
        result.put("start", earliestYearMonth);
        result.put("end", latestYearMonth);
        return result;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
