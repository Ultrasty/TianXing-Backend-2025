package com.tongji.enso.mybatisdemo.service.online;

import com.tongji.enso.mybatisdemo.entity.online.Tj_nao;
import com.tongji.enso.mybatisdemo.mapper.online.Tj_naoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class Tj_naoService {

    @Autowired
    private Tj_naoMapper tj_naomapper;

    public Tj_nao findPredictionByMonthAndModel(String year, String month){
        return tj_naomapper.findByMonthAndModel(year,month);
    }

    public Tj_nao findGridByMonth(String year, String month) {
        return tj_naomapper.findGridByMonth(year,month);
    }

    public List<Tj_nao> findNAOByModel(String var_model){
        return tj_naomapper.findAllByModel(var_model);
    }
    public boolean isValidDate(String year, String month) {
    try {
        int y = Integer.parseInt(year);
        int m = Integer.parseInt(month);
        return m >= 1 && m <= 12;
    } catch (NumberFormatException e) {
        return false;
    }
    }
    public List<String> getAvailableYears() {
        return tj_naomapper.findAvailableYears();
    }

    public List<String> getMonthsByYear(String year) {
        return tj_naomapper.findMonthsByYear(year);
    }

}
