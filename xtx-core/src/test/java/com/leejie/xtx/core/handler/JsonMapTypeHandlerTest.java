package com.leejie.xtx.core.handler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonMapTypeHandlerTest {

    private final JsonMapTypeHandler handler = new JsonMapTypeHandler();

    @Test
    @DisplayName("写库：Map 序列化为 JSON 对象字符串")
    void setNonNullParameter_writesJsonObject() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, Map.of("color", "#7C3AED"), JdbcType.VARCHAR);
        verify(ps).setString(1, "{\"color\":\"#7C3AED\"}");
    }

    @Test
    @DisplayName("读库：JSON 对象还原为 Map")
    void getNullableResult_parsesJsonObject() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("extra")).thenReturn("{\"color\":\"#7C3AED\"}");
        assertEquals(Map.of("color", "#7C3AED"), handler.getNullableResult(rs, "extra"));
    }

    @Test
    @DisplayName("读库：NULL 列还原为 null（extra 可选，与 images 的空数组语义不同）")
    void getNullableResult_null_returnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("extra")).thenReturn(null);
        assertNull(handler.getNullableResult(rs, "extra"));
    }

    @Test
    @DisplayName("读库：空白字符串同样还原为 null")
    void getNullableResult_blank_returnsNull() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("extra")).thenReturn("  ");
        assertNull(handler.getNullableResult(rs, "extra"));
    }

    @Test
    @DisplayName("读库：按列下标与 CallableStatement 两条路径同样可用")
    void getNullableResult_byIndex_andCallable() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("{\"color\":\"#fff\"}");
        assertEquals(Map.of("color", "#fff"), handler.getNullableResult(rs, 1));

        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(1)).thenReturn("{\"color\":\"#000\"}");
        assertEquals(Map.of("color", "#000"), handler.getNullableResult(cs, 1));
    }

    @Test
    @DisplayName("读库：脏数据抛 SQLException，而非 RuntimeException")
    void getNullableResult_malformed_throwsSqlException() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("extra")).thenReturn("not-json");
        assertThrows(SQLException.class, () -> handler.getNullableResult(rs, "extra"));
    }
}
