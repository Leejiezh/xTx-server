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