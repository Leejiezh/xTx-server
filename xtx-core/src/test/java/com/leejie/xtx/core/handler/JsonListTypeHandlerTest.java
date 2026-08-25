package com.leejie.xtx.core.handler;

import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JsonListTypeHandlerTest {

    private final JsonListTypeHandler handler = new JsonListTypeHandler();

    @Test
    @DisplayName("写库：List 序列化为 JSON 数组字符串")
    void setNonNullParameter_writesJsonArray() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, List.of("a.jpg", "b.jpg"), JdbcType.VARCHAR);
        verify(ps).setString(1, "[\"a.jpg\",\"b.jpg\"]");
    }

    @Test
    @DisplayName("写库：空 List 序列化为 []，而非 null")
    void setNonNullParameter_emptyList_writesEmptyArray() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, List.of(), JdbcType.VARCHAR);
        verify(ps).setString(1, "[]");
    }

    @Test
    @DisplayName("读库：JSON 数组还原为 List，保持顺序")
    void getNullableResult_parsesJsonArray() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("images")).thenReturn("[\"a.jpg\",\"b.jpg\"]");
        assertEquals(List.of("a.jpg", "b.jpg"), handler.getNullableResult(rs, "images"));
    }

    @Test
    @DisplayName("读库：NULL 列返回空 List，调用方不必判空")
    void getNullableResult_null_returnsEmpty() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("images")).thenReturn(null);
        assertTrue(handler.getNullableResult(rs, "images").isEmpty());
    }

    @Test
    @DisplayName("读库：空白字符串同样返回空 List")
    void getNullableResult_blank_returnsEmpty() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("images")).thenReturn("  ");
        assertTrue(handler.getNullableResult(rs, "images").isEmpty());
    }

    @Test
    @DisplayName("读库：按列下标与 CallableStatement 两条路径同样可用")
    void getNullableResult_byIndex_andCallable() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString(1)).thenReturn("[\"x.png\"]");
        assertEquals(List.of("x.png"), handler.getNullableResult(rs, 1));

        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(1)).thenReturn("[\"y.png\"]");
        assertEquals(List.of("y.png"), handler.getNullableResult(cs, 1));
    }

    @Test
    @DisplayName("读库：脏数据抛 SQLException 而非 RuntimeException，让 MyBatis 能包成 DataAccessException")
    void getNullableResult_malformed_throwsSqlException() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("images")).thenReturn("not-json");
        assertThrows(SQLException.class, () -> handler.getNullableResult(rs, "images"));
    }
}
