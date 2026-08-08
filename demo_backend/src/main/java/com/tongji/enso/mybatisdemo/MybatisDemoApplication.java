package com.tongji.enso.mybatisdemo;

import org.mybatis.spring.annotation.MapperScan;
import org.mybatis.spring.annotation.MapperScans;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScans({
        @MapperScan("com.tongji.enso.mybatisdemo.mapper.online"),
        @MapperScan(basePackages = {
                "com.tongji.enso.mybatisdemo.mapper.admin",
                "com.tongji.enso.mybatisdemo.admin.auth",
                "com.tongji.enso.mybatisdemo.admin.evaluation"
        }, annotationClass = Mapper.class)
})
public class MybatisDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MybatisDemoApplication.class, args);
    }

}
