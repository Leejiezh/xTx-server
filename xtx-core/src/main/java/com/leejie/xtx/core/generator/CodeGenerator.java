package com.leejie.xtx.core.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.sql.Types;
import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器主入口
 *
 * <p>运行前请确认数据库连接信息正确，会覆盖已存在的文件。
 * 只生成 Entity/Mapper/XML/Service/ServiceImpl，
 * DTO 和 Controller 由 {@link DtoGenerator} 生成。
 */
public class CodeGenerator {

    static final String URL = "jdbc:mysql://localhost:3306/xtx?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
    static final String USERNAME = "root";
    static final String PASSWORD = "root";

    /** 要生成的表（排除 user 表） */
    static final String[] TABLES = {"record", "report"};

    public static void main(String[] args) {
        String projectPath = System.getProperty("user.dir");

        FastAutoGenerator.create(URL, USERNAME, PASSWORD)
                .globalConfig(builder -> builder
                        .author("leejie")
                        .outputDir(projectPath + "/xtx-core/src/main/java")
                        .disableOpenDir()
                )
                .dataSourceConfig(builder -> builder
                        .typeConvertHandler((globalConfig, typeRegistry, metaInfo) -> {
                            int type = metaInfo.getJdbcType().TYPE_CODE;
                            if (type == Types.SMALLINT) {
                                return DbColumnType.INTEGER;
                            }
                            return typeRegistry.getColumnType(metaInfo);
                        })
                )
                .packageConfig(builder -> builder
                        .parent("com.leejie.xtx.core")
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .pathInfo(Collections.singletonMap(
                                OutputFile.xml,
                                projectPath + "/xtx-core/src/main/resources/mapper"))
                )
                .strategyConfig(builder -> {
                    builder.addInclude(TABLES)
                            .entityBuilder()
                            .enableLombok()
                            .logicDeleteColumnName("deleted")
                            .disableSerialVersionUID()
                            .controllerBuilder()
                            .disable()
                            .serviceBuilder()
                            .formatServiceFileName("%sService");
                })
                .templateConfig(builder -> builder
                        .entity("/templates/entity.java.vm")
                        .service("/templates/service.java.vm")
                        .serviceImpl("/templates/serviceImpl.java.vm")
                        .mapper("/templates/mapper.java.vm")
                        .xml(null)
                )
                .templateEngine(new VelocityTemplateEngine())
                .execute();

        // 第二步：生成 DTO 和 Controller
        DtoGenerator.generate(TABLES);
    }
}