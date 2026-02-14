package com.boyan.vir;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.boyan.vir.mapper")
public class VirtualAiApplication {
    public static void main(String[] args) {
        SpringApplication.run(VirtualAiApplication.class, args);
    }
}
