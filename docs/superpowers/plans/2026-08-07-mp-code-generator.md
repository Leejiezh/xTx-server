# MP 代码生成器 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一个运行 `CodeGenerator.main()` 即可根据数据库表生成完整 Controller → Service → Entity → Mapper 代码的工具，所有生成代码都继承项目的基类体系并携带 API 文档注解。

**Architecture:** 生成器位于独立 Maven 模块 `xtx-code-generator`（包 `com.leejie.xtx.generator`）。双层生成器：FastAutoGenerator 负责 Entity/Mapper/Service/ServiceImpl（通过自定义 VM 模板适配基类），DtoGenerator 独立处理 DTO/Controller（JDBC 读表结构 + Velocity 渲染）。所有模板文件集中在 `xtx-code-generator/src/main/resources/templates/`。生成的业务代码输出到 `xtx-core`。

**Tech Stack:** MyBatis-Plus Generator 3.5.9, Velocity 2.3, SpringDoc 2.7.0, JDBC (MySQL), Java 21

## Global Constraints

- 排除 user 表，不生成 user 表相关的任何代码
- Entity 全部继承 `OwnedEntity`（含 userId, deleted, 逻辑删除）
- ServiceImpl 全部继承 `OwnedServiceImpl<Mapper, Entity>`
- 全部类/字段/方法均携带 SpringDoc 注解（`@Schema`, `@Tag`, `@Operation`）
- DTO 使用 `jakarta.validation` 校验注解（`@NotBlank`, `@NotNull` 等）
- 统一响应体使用 `R<T>`（`R.ok(data)`），分页使用 `PageResult.of(page, XxxVO::fromEntity)`
- 生成代码路径：`xtx-core/src/main/java/com/leejie/xtx/core/` 下按分层分包；生成器自身代码与模板位于独立模块 `xtx-code-generator/`（包 `com.leejie.xtx.generator`）
- 生成器可反复运行，覆盖已存在文件

---

### Task 1: 新增 springdoc 依赖

**Files:**
- Modify: `pom.xml` — 父 pom 添加依赖管理
- Modify: `xtx-core/pom.xml` — 引入 springdoc 依赖

**Interfaces:**
- Consumes: 无
- Produces: 项目获得 `io.swagger.v3.oas.annotations` 包，后续 VM 模板可以引用 `@Schema`, `@Tag`, `@Operation`

- [ ] **Step 1：父 pom 增加 springdoc 版本属性**

在 `<properties>` 中添加：
```xml
<springdoc.version>2.7.0</springdoc.version>
```

- [ ] **Step 2：父 pom 增加 springdoc 依赖管理**

在 `<dependencyManagement><dependencies>` 中添加：
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>${springdoc.version}</version>
</dependency>
```

- [ ] **Step 3：xtx-core/pom.xml 引入 springdoc**

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
</dependency>
```

- [ ] **Step 4：验证编译**

```bash
mvn compile -pl xtx-core -am -q
```
Expected: BUILD SUCCESS

- [ ] **Step 5：提交**

```bash
git add pom.xml xtx-core/pom.xml
git commit -m "feat(deps): 集成 springdoc-openapi 2.7.0 用于 API 文档注解"
```

---

### Task 2: 创建模板目录和 Entity/Service/ServiceImpl/Mapper 的 VM 模板

**Files:**
- Create: `xtx-code-generator/src/main/resources/templates/entity.java.vm`
- Create: `xtx-code-generator/src/main/resources/templates/service.java.vm`
- Create: `xtx-code-generator/src/main/resources/templates/serviceImpl.java.vm`
- Create: `xtx-code-generator/src/main/resources/templates/mapper.java.vm`

**Interfaces:**
- Consumes: 数据库表结构（FastAutoGenerator 传入 `table`, `package`, `entity` 等变量）
- Produces: 4 个模板文件，供 FastAutoGenerator 渲染时使用

- [ ] **Step 1：创建模板目录**

```bash
mkdir -p xtx-code-generator/src/main/resources/templates/dto
```

- [ ] **Step 2：编写 entity.java.vm**

```java
package ${package.Entity};

import com.leejie.xtx.common.base.entity.OwnedEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

## 基类(BaseEntity/OwnedEntity)已声明这些字段，实体生成时跳过，避免重复声明
#set($baseFields = ["userId", "createdAt", "updatedAt", "deleted"])

## 检测字段类型，按需导入 java.time / java.math 包
#set($importLocalDateTime = false)
#set($importLocalDate = false)
#set($importLocalTime = false)
#set($importBigDecimal = false)
#foreach($field in ${table.fields})
#if(!${field.keyFlag} && !${baseFields.contains($field.propertyName)})
#if(${field.propertyType} == "LocalDateTime")#set($importLocalDateTime = true)#end
#if(${field.propertyType} == "LocalDate")#set($importLocalDate = true)#end
#if(${field.propertyType} == "LocalTime")#set($importLocalTime = true)#end
#if(${field.propertyType} == "BigDecimal")#set($importBigDecimal = true)#end
#end
#end
#if($importLocalDateTime)
import java.time.LocalDateTime;
#end
#if($importLocalDate)
import java.time.LocalDate;
#end
#if($importLocalTime)
import java.time.LocalTime;
#end
#if($importBigDecimal)
import java.math.BigDecimal;
#end

#if(${table.comment})
@Schema(description = "${table.comment}")
#end
@Data
@EqualsAndHashCode(callSuper = true)
public class ${entity} extends OwnedEntity {

#foreach($field in ${table.fields})
    #if(!${field.keyFlag} && !${baseFields.contains($field.propertyName)})
    #if(${field.comment})
    /** ${field.comment} */
    @Schema(description = "${field.comment}")
    #end
    private ${field.propertyType} ${field.propertyName};

    #end
#end
}
```

- [ ] **Step 3：编写 service.java.vm**

```java
package ${package.Service};

import com.leejie.xtx.common.base.service.OwnedService;
import ${package.Entity}.${entity};

/**
 * $!{table.comment} 服务接口
 */
public interface ${table.serviceName} extends OwnedService<${entity}> {
}
```

- [ ] **Step 4：编写 serviceImpl.java.vm**

```java
package ${package.ServiceImpl};

import com.leejie.xtx.common.base.service.impl.OwnedServiceImpl;
import ${package.Entity}.${entity};
import ${package.Mapper}.${table.mapperName};
import ${package.Service}.${table.serviceName};
import org.springframework.stereotype.Service;

/**
 * $!{table.comment} 服务实现
 */
@Service
public class ${table.serviceImplName} extends OwnedServiceImpl<${table.mapperName}, ${entity}> implements ${table.serviceName} {
}
```

- [ ] **Step 5：编写 mapper.java.vm**

```java
package ${package.Mapper};

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import ${package.Entity}.${entity};

/**
 * $!{table.comment} Mapper
 */
public interface ${table.mapperName} extends BaseMapper<${entity}> {
}
```

- [ ] **Step 6：提交**

```bash
git add xtx-code-generator/src/main/resources/templates/
git commit -m "feat(generator): 添加 Entity/Service/ServiceImpl/Mapper 的 VM 模板"
```

---

### Task 3: 重写 CodeGenerator.java（FastAutoGenerator 部分）

**Files:**
- Modify: `xtx-code-generator/src/main/java/com/leejie/xtx/generator/CodeGenerator.java`

**Interfaces:**
- Consumes: 数据库连接信息 + tables 数组 + `xtx-code-generator/src/main/resources/templates/` 下的 VM 模板
- Produces: Entity/Mapper/XML/Service/ServiceImpl 文件到 `xtx-core/src/main/java/com/leejie/xtx/core/` 下对应分层分包

- [ ] **Step 1：编写 CodeGenerator.java**

```java
package com.leejie.xtx.generator;

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
    static final String PASSWORD = "123456";

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
                            if (type == Types.SMALLINT || type == Types.TINYINT) {
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
```

- [ ] **Step 2：提交**

```bash
git add xtx-code-generator/src/main/java/com/leejie/xtx/generator/CodeGenerator.java
git commit -m "feat(generator): 重写 CodeGenerator，使用自定义 VM 模板适配基类体系"
```

---

### Task 4: 创建 DTO/Controller 的 VM 模板

**Files:**
- Create: `xtx-code-generator/src/main/resources/templates/dto/CreateReq.java.vm`
- Create: `xtx-code-generator/src/main/resources/templates/dto/UpdateReq.java.vm`
- Create: `xtx-code-generator/src/main/resources/templates/dto/VO.java.vm`
- Create: `xtx-code-generator/src/main/resources/templates/dto/Controller.java.vm`

**Interfaces:**
- Consumes: 模板变量 `{packageName, className, tableComment, fields}`（由 DtoGenerator 传入）
- Produces: 4 个模板文件，供 DtoGenerator 渲染时使用

- [ ] **Step 1：编写 CreateReq.java.vm**

DTO 模板接受的变量：`${packageName}`（如 `com.leejie.xtx.core.dto`），`${className}`（如 `RecordCreateReq`），`${tableComment}`，`${fields}`（字段列表，每个字段有 `name`, `type`, `comment`, `nullable`）

```java
package ${packageName};

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

#if(${tableComment})
@Schema(description = "${tableComment}创建请求")
#end
@Data
public class ${className} {

#foreach($field in $fields)
    #if(${field.comment})
    @Schema(description = "${field.comment}")
    #end
    #if(!${field.nullable})
        #if(${field.type} == "String")
    @NotBlank(message = "${field.comment}不能为空")
        #else
    @NotNull(message = "${field.comment}不能为空")
        #end
    #end
    private ${field.type} ${field.name};
#end
}
```

注意：字段列表已经从 DtoGenerator 中排除了 id/createdAt/updatedAt/userId/deleted。

- [ ] **Step 2：编写 UpdateReq.java.vm**

```java
package ${packageName};

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

#if(${tableComment})
@Schema(description = "${tableComment}更新请求")
#end
@Data
public class ${className} {

    @NotNull(message = "id不能为空")
    @Schema(description = "主键ID")
    private Long id;

#foreach($field in $fields)
    #if(${field.comment})
    @Schema(description = "${field.comment}")
    #end
    #if(!${field.nullable})
    @NotNull(message = "${field.comment}不能为空")
    #end
    private ${field.type} ${field.name};
#end
}
```

- [ ] **Step 3：编写 VO.java.vm**

```java
package ${packageName};

import ${entityFullName};
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

#if(${tableComment})
@Schema(description = "${tableComment}视图对象")
#end
@Data
public class ${className} {

#foreach($field in $fields)
    #if(${field.comment})
    @Schema(description = "${field.comment}")
    #end
    private ${field.type} ${field.name};
#end

    public static ${className} fromEntity(${entityName} entity) {
        ${className} vo = new ${className}();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
```

- [ ] **Step 4：编写 Controller.java.vm**

```java
package ${packageName};

import com.leejie.xtx.common.base.query.PageQuery;
import com.leejie.xtx.common.base.vo.PageResult;
import com.leejie.xtx.common.result.R;
import ${serviceFullName};
import ${createReqFullName};
import ${updateReqFullName};
import ${voFullName};
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "${tableComment}管理")
@RestController
@RequestMapping("/${mapping}")
@RequiredArgsConstructor
public class ${className} {

    private final ${serviceName} ${serviceVar};

    @PostMapping
    @Operation(summary = "创建${tableComment}")
    public R<Long> create(@Valid @RequestBody ${createReqName} req) {
        return R.ok(${serviceVar}.create(req.toEntity()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询${tableComment}详情")
    public R<${voName}> get(@PathVariable Long id) {
        return R.ok(${voName}.fromEntity(${serviceVar}.get(id)));
    }

    @PutMapping
    @Operation(summary = "更新${tableComment}")
    public R<Void> update(@Valid @RequestBody ${updateReqName} req) {
        ${serviceVar}.update(req.toEntity());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除${tableComment}")
    public R<Void> delete(@PathVariable Long id) {
        ${serviceVar}.delete(id);
        return R.ok();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询${tableComment}")
    public R<PageResult<${voName}>> page(PageQuery query) {
        return R.ok(PageResult.of(${serviceVar}.page(query, null), ${voName}::fromEntity));
    }
}
```

- [ ] **Step 5：提交**

```bash
git add xtx-code-generator/src/main/resources/templates/dto/
git commit -m "feat(generator): 添加 DTO/Controller 的 VM 模板"
```

---

### Task 5: 实现 DtoGenerator.java

**Files:**
- Create: `xtx-code-generator/src/main/java/com/leejie/xtx/generator/DtoGenerator.java`

**Interfaces:**
- Consumes: `String[] tables`（表名数组），数据库连接信息（与 CodeGenerator 共享）
- Produces: CreateReq/UpdateReq/VO/Controller 文件到 `xtx-core/src/main/java/com/leejie/xtx/core/` 下对应分层分包

- [ ] **Step 1：编写 DtoGenerator.java**

```java
package com.leejie.xtx.generator;

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;

import java.io.FileWriter;
import java.io.Writer;
import java.sql.*;
import java.util.*;

/**
 * DTO 和 Controller 生成器。
 *
 * <p>通过 JDBC 读取表结构，使用 Velocity 模板引擎渲染 DTO/Controller 文件。
 * 独立于 FastAutoGenerator，因为 MP 生成器不支持 DTO 类型的输出。
 */
public class DtoGenerator {

    private static final String URL = CodeGenerator.URL;
    private static final String USERNAME = CodeGenerator.USERNAME;
    private static final String PASSWORD = CodeGenerator.PASSWORD;
    private static final String BASE_PACKAGE = "com.leejie.xtx.core";
    private static final String OUTPUT_DIR = System.getProperty("user.dir") + "/xtx-core/src/main/java";
    private static final String DTO_PACKAGE = BASE_PACKAGE + ".dto";
    private static final String CONTROLLER_PACKAGE = BASE_PACKAGE + ".controller";
    private static final String ENTITY_PACKAGE = BASE_PACKAGE + ".entity";
    private static final String SERVICE_PACKAGE = BASE_PACKAGE + ".service";

    /**
     * 需要排除的基类字段 — 这些字段在 OwnedEntity 中已定义，DTO 中不出现
     */
    private static final Set<String> BASE_FIELDS = Set.of("id", "createdAt", "updatedAt", "userId", "deleted");

    /**
     * MySQL 类型 → Java 类型映射
     */
    private static final Map<String, String> TYPE_MAP = new HashMap<>();

    static {
        TYPE_MAP.put("VARCHAR", "String");
        TYPE_MAP.put("CHAR", "String");
        TYPE_MAP.put("TEXT", "String");
        TYPE_MAP.put("LONGTEXT", "String");
        TYPE_MAP.put("INT", "Integer");
        TYPE_MAP.put("INT UNSIGNED", "Integer");
        TYPE_MAP.put("TINYINT", "Integer");
        TYPE_MAP.put("SMALLINT", "Integer");
        TYPE_MAP.put("BIGINT", "Long");
        TYPE_MAP.put("BIGINT UNSIGNED", "Long");
        TYPE_MAP.put("DECIMAL", "BigDecimal");
        TYPE_MAP.put("FLOAT", "Float");
        TYPE_MAP.put("DOUBLE", "Double");
        TYPE_MAP.put("DATE", "LocalDate");
        TYPE_MAP.put("DATETIME", "LocalDateTime");
        TYPE_MAP.put("TIMESTAMP", "LocalDateTime");
        TYPE_MAP.put("TIME", "LocalTime");
        TYPE_MAP.put("BOOLEAN", "Boolean");
        TYPE_MAP.put("BLOB", "byte[]");
        TYPE_MAP.put("JSON", "String");
    }

    public static void generate(String... tables) {
        VelocityEngine engine = createVelocityEngine();

        try (Connection conn = DriverManager.getConnection(URL, USERNAME, PASSWORD)) {
            for (String tableName : tables) {
                if ("user".equalsIgnoreCase(tableName)) {
                    continue;
                }
                generateForTable(conn, engine, tableName);
            }
        } catch (Exception e) {
            throw new RuntimeException("DTO 生成失败", e);
        }

        System.out.println("=== DTO/Controller 生成完成 ===");
    }

    private static void generateForTable(Connection conn, VelocityEngine engine, String tableName) throws Exception {
        String tableComment = getTableComment(conn, tableName);
        List<FieldInfo> columns = getColumns(conn, tableName);
        String entityName = toPascalCase(tableName);
        String className = toPascalCase(tableName);

        // 生成 CreateReq
        renderDto(engine, "/templates/dto/CreateReq.java.vm", DTO_PACKAGE,
                className + "CreateReq", tableComment, filterCreateFields(columns),
                entityName, ENTITY_PACKAGE + "." + entityName);

        // 生成 UpdateReq
        renderDto(engine, "/templates/dto/UpdateReq.java.vm", DTO_PACKAGE,
                className + "UpdateReq", tableComment, filterUpdateFields(columns),
                entityName, ENTITY_PACKAGE + "." + entityName);

        // 生成 VO（包含所有业务字段 + id）
        renderDto(engine, "/templates/dto/VO.java.vm", DTO_PACKAGE,
                className + "VO", tableComment, getAllVoFields(conn, tableName, columns),
                entityName, ENTITY_PACKAGE + "." + entityName);

        // 生成 Controller
        renderController(engine, tableName, className, tableComment, entityName);
    }

    private static void renderDto(VelocityEngine engine, String templatePath,
                                  String packageName, String className,
                                  String tableComment, List<FieldInfo> fields,
                                  String entityName, String entityFullName) throws Exception {
        VelocityContext ctx = new VelocityContext();
        ctx.put("packageName", packageName);
        ctx.put("className", className);
        ctx.put("tableComment", tableComment != null ? tableComment : className);
        ctx.put("fields", fields);
        ctx.put("entityName", entityName);
        ctx.put("entityFullName", entityFullName);
        ctx.put("imports", collectImports(fields));

        String outputPath = OUTPUT_DIR + "/" + packageName.replace('.', '/') + "/" + className + ".java";
        ensureParentDir(outputPath);

        try (Writer writer = new FileWriter(outputPath)) {
            Template template = engine.getTemplate(templatePath, "UTF-8");
            template.merge(ctx, writer);
        }

        System.out.println(" 生成: " + outputPath);
    }

    private static void renderController(VelocityEngine engine, String tableName,
                                         String className, String tableComment,
                                         String entityName) throws Exception {
        String serviceVar = toCamelCase(className) + "Service";

        VelocityContext ctx = new VelocityContext();
        ctx.put("packageName", CONTROLLER_PACKAGE);
        ctx.put("className", className + "Controller");
        ctx.put("tableComment", tableComment != null ? tableComment : className);
        ctx.put("mapping", toKebabCase(tableName));
        ctx.put("serviceName", className + "Service");
        ctx.put("serviceVar", serviceVar);
        ctx.put("serviceFullName", SERVICE_PACKAGE + "." + className + "Service");
        ctx.put("createReqName", className + "CreateReq");
        ctx.put("createReqFullName", DTO_PACKAGE + "." + className + "CreateReq");
        ctx.put("updateReqName", className + "UpdateReq");
        ctx.put("updateReqFullName", DTO_PACKAGE + "." + className + "UpdateReq");
        ctx.put("voName", className + "VO");
        ctx.put("voFullName", DTO_PACKAGE + "." + className + "VO");

        String outputPath = OUTPUT_DIR + "/" + CONTROLLER_PACKAGE.replace('.', '/') + "/" + className + "Controller.java";
        ensureParentDir(outputPath);

        try (Writer writer = new FileWriter(outputPath)) {
            Template template = engine.getTemplate("/templates/dto/Controller.java.vm", "UTF-8");
            template.merge(ctx, writer);
        }

        System.out.println(" 生成: " + outputPath);
    }

    // ---- 数据库操作 ----

    private static String getTableComment(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT TABLE_COMMENT FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("TABLE_COMMENT");
                }
            }
        }
        return null;
    }

    private static List<FieldInfo> getColumns(Connection conn, String tableName) throws SQLException {
        String sql = "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT, COLUMN_KEY " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                "ORDER BY ORDINAL_POSITION";
        List<FieldInfo> fields = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String javaName = toCamelCase(columnName);

                    if (BASE_FIELDS.contains(javaName)) {
                        continue;
                    }

                    String dbType = rs.getString("DATA_TYPE").toUpperCase();
                    String javaType = TYPE_MAP.getOrDefault(dbType, "String");
                    boolean nullable = "YES".equalsIgnoreCase(rs.getString("IS_NULLABLE"));
                    String comment = rs.getString("COLUMN_COMMENT");
                    boolean isPk = "PRI".equalsIgnoreCase(rs.getString("COLUMN_KEY"));

                    fields.add(new FieldInfo(javaName, javaType, comment, nullable, isPk));
                }
            }
        }
        return fields;
    }

    private static List<FieldInfo> filterCreateFields(List<FieldInfo> fields) {
        return fields.stream().filter(f -> !f.isPk).toList();
    }

    private static List<FieldInfo> filterUpdateFields(List<FieldInfo> fields) {
        return fields.stream().filter(f -> !f.isPk).toList();
    }

    private static List<FieldInfo> getAllVoFields(Connection conn, String tableName,
                                                  List<FieldInfo> businessFields) throws SQLException {
        List<FieldInfo> allFields = new ArrayList<>();

        String sql = "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_COMMENT " +
                "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? " +
                "AND COLUMN_KEY = 'PRI' ORDER BY ORDINAL_POSITION";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String dbType = rs.getString("DATA_TYPE").toUpperCase();
                    String javaType = TYPE_MAP.getOrDefault(dbType, "String");
                    String comment = rs.getString("COLUMN_COMMENT");
                    allFields.add(new FieldInfo(toCamelCase(columnName), javaType, comment, false, true));
                }
            }
        }

        allFields.addAll(businessFields);
        return allFields;
    }

    // ---- 工具方法 ----

    private static VelocityEngine createVelocityEngine() {
        VelocityEngine engine = new VelocityEngine();
        engine.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        engine.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        engine.setProperty("input.encoding", "UTF-8");
        engine.setProperty("output.encoding", "UTF-8");
        engine.init();
        return engine;
    }

    private static String toPascalCase(String name) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : name.toCharArray()) {
            if (c == '_') {
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String toCamelCase(String name) {
        String pascal = toPascalCase(name);
        if (pascal.isEmpty()) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    private static String toKebabCase(String name) {
        return name.toLowerCase().replace('_', '-');
    }

    private static void ensureParentDir(String filePath) {
        java.io.File file = new java.io.File(filePath);
        java.io.File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }

    /** 计算字段类型需要的额外 import（java.time / java.math） */
    private static List<String> collectImports(List<FieldInfo> fields) {
        Map<String, String> typeToImport = new HashMap<>();
        typeToImport.put("BigDecimal", "java.math.BigDecimal");
        typeToImport.put("LocalDate", "java.time.LocalDate");
        typeToImport.put("LocalDateTime", "java.time.LocalDateTime");
        typeToImport.put("LocalTime", "java.time.LocalTime");

        List<String> imports = new ArrayList<>();
        for (FieldInfo f : fields) {
            String imp = typeToImport.get(f.type);
            if (imp != null && !imports.contains(imp)) {
                imports.add(imp);
            }
        }
        Collections.sort(imports);
        return imports;
    }

    // ---- 内部类 ----

    public static class FieldInfo {
        final String name;
        final String type;
        final String comment;
        final boolean nullable;
        final boolean isPk;

        FieldInfo(String name, String type, String comment, boolean nullable, boolean isPk) {
            this.name = name;
            this.type = type;
            this.comment = comment != null ? comment : "";
            this.nullable = nullable;
            this.isPk = isPk;
        }

        public String getName() { return name; }
        public String getType() { return type; }
        public String getComment() { return comment; }
        public boolean isNullable() { return nullable; }
        public boolean isPk() { return isPk; }
    }
}
```

- [ ] **Step 2：提交**

```bash
git add xtx-code-generator/src/main/java/com/leejie/xtx/generator/DtoGenerator.java
git commit -m "feat(generator): 实现 DtoGenerator — JDBC + Velocity 生成 DTO/Controller"
```

---

### Task 6: 验证生成器可运行

**Files:**
- No new files
- 验证：连接数据库，运行 `CodeGenerator.main()`，检查生成的文件

- [ ] **Step 1：确认数据库可用**

确保 MySQL 运行、xtx 数据库存在、有表结构（record, report）。

- [ ] **Step 2：编译项目**

```bash
mvn compile -pl xtx-code-generator -am -q
```

- [ ] **Step 3：运行 CodeGenerator.main()**

在 IDE 中运行 `CodeGenerator.main()`，或在命令行：
```bash
mvn exec:java -pl xtx-code-generator -Dexec.mainClass="com.leejie.xtx.generator.CodeGenerator" -Dexec.classpathScope=compile
```

- [ ] **Step 4：检查生成的文件**

确认以下文件已生成（以 record 表为例）：
```
xtx-core/src/main/java/com/leejie/xtx/core/entity/RecordEntity.java
xtx-core/src/main/java/com/leejie/xtx/core/mapper/RecordMapper.java
xtx-core/src/main/resources/mapper/RecordMapper.xml
xtx-core/src/main/java/com/leejie/xtx/core/service/RecordService.java
xtx-core/src/main/java/com/leejie/xtx/core/service/impl/RecordServiceImpl.java
xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordCreateReq.java
xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordUpdateReq.java
xtx-core/src/main/java/com/leejie/xtx/core/dto/RecordVO.java
xtx-core/src/main/java/com/leejie/xtx/core/controller/RecordController.java
```

- [ ] **Step 5：验证继承关系**

抽样检查生成的文件：
- `RecordEntity.java` extends `OwnedEntity`，含 `@Schema` 注解
- `RecordServiceImpl.java` extends `OwnedServiceImpl<RecordMapper, RecordEntity>`
- `RecordCreateReq.java` 含 `@Schema` 和 `@NotBlank`/`@NotNull`
- `RecordController.java` 含 `@Tag`、`@Operation`，5 个 RESTful 端点

- [ ] **Step 6：编译验证生成代码无语法错误**

```bash
mvn compile -pl xtx-core -am
```- [ ] **Step 7：全部提交**

```bash
git add -A
git commit -m "feat(generator): 完成 MP 代码生成器，支持基类继承和 API 文档注解"
```