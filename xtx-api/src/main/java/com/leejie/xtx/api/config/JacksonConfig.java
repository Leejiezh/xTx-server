package com.leejie.xtx.api.config;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 JSON 序列化定制。
 *
 * <p>只把装箱 {@code Long} 序列化成字符串：MyBatis-Plus 雪花主键（19 位）超出
 * JS Number.MAX_SAFE_INTEGER（2^53-1），小程序端 JSON.parse 会舍入丢精度，
 * 导致详情/编辑按错误 id 查询（light-note 前端 issue）。
 * 基本类型 {@code long} 不注册 —— PageResult.total/pageNum/pageSize 等分页数字
 * 仍需按数字下发，前端要做加法/比较。
 */
@Configuration
public class JacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer longIdToString() {
        return builder -> builder.serializerByType(Long.class, ToStringSerializer.instance);
    }
}
