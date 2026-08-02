package com.leejie.xtx.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.leejie.xtx"})
public class XTxAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(XTxAdminApplication.class, args);
    }
}