package com.leejie.xtx.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.leejie.xtx"})
public class XTxApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XTxApiApplication.class, args);
    }
}