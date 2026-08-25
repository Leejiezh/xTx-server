package com.leejie.xtx.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** @EnableScheduling 是 OrphanFileSweeper 生效的前提，缺了它孤儿文件永不清理 */
@SpringBootApplication
@ComponentScan(basePackages = {"com.leejie.xtx"})
@EnableScheduling
public class XTxApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(XTxApiApplication.class, args);
    }
}