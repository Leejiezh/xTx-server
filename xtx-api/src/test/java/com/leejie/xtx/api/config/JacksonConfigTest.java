package com.leejie.xtx.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 雪花 Long 主键精度回归测试。
 *
 * <p>雪花 ID（19 位，如 1789123456789012345）远超 JS Number.MAX_SAFE_INTEGER
 * （2^53-1 ≈ 9007199254740991，16 位）。若按 JSON 数字下发，小程序端 JSON.parse
 * 会舍入丢精度，详情/编辑查不到记录（见 light-note 前端 issue）。
 * 因此装箱 {@code Long} 必须序列化成字符串；基本类型 {@code long}（分页数字）不动。
 */
class JacksonConfigTest {

    private final ObjectMapper mapper = buildMapper();

    private static ObjectMapper buildMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonConfig().longIdToString().customize(builder);
        return builder.build();
    }

    @Test
    @DisplayName("装箱 Long 序列化为字符串（雪花 ID 不丢精度）")
    void boxedLongSerializesAsString() throws Exception {
        Long snowflake = 1789123456789012345L; // > 2^53-1，模拟雪花主键
        assertEquals("{\"id\":\"1789123456789012345\"}", mapper.writeValueAsString(Map.of("id", snowflake)));
    }

    @Test
    @DisplayName("基本类型 long 仍是数字（PageResult.total 等分页数字不受影响）")
    void primitiveLongStaysNumeric() throws Exception {
        Page p = new Page();
        p.total = 3;
        assertEquals("{\"total\":3}", mapper.writeValueAsString(p));
    }

    /** 含基本类型 long 字段的 POJO，模拟 PageResult.total */
    static class Page {
        public long total;
    }
}
