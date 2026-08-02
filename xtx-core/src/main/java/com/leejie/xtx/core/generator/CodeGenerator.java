package com.leejie.xtx.core.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.DbColumnType;

import java.sql.Types;
import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器
 * 运行前请修改数据库连接信息
 */
public class CodeGenerator {

    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/xtx?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai";
        String username = "root";
        String password = "root";

        String projectPath = System.getProperty("user.dir");
        // 如果是在 xtx-core 模块下运行，需要调整输出路径
        String outputDir = projectPath + "/xtx-core/src/main/java";

        FastAutoGenerator.create(url, username, password)
                .globalConfig(builder -> builder
                        .author("leejie")
                        .outputDir(outputDir)
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
                        .entity("user.entity")
                        .mapper("user.mapper")
                        .service("user.service")
                        .serviceImpl("user.service.impl")
                        .pathInfo(Collections.singletonMap(OutputFile.xml, projectPath + "/xtx-core/src/main/resources/mapper"))
                )
                .strategyConfig(builder -> builder
                        .addInclude("user", "goods") // 替换为实际的表名
                        .entityBuilder()
                        .enableLombok()
                        .logicDeleteColumnName("deleted")
                        .controllerBuilder()
                        .disable()
                        .serviceBuilder()
                        .formatServiceFileName("%sService")
                )
                .execute();
    }
}