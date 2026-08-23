package com.leejie.xtx.core;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.leejie.xtx.core.entity.Record;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 MyBatis-Plus 3.5.9 下，从基类 OwnedEntity 继承的 userId 字段
 * 能否通过 LambdaQueryWrapper 方法引用解析（getter 声明在父类）。
 */
class InheritedFieldLambdaTest {

    @Test
    void inheritedUserIdInLambdaResolves() {
        // 模拟 mapper 扫描：Spring 启动时 MyBatis-Plus 会自动为 Record 注册 TableInfo，
        // 这里手动等价注册，让 columnMap 就绪
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Record.class);

        LambdaQueryWrapper<Record> wrapper = new LambdaQueryWrapper<Record>()
                .eq(Record::getUserId, 1L);

        String sql = wrapper.getSqlSegment();
        System.out.println("sqlSegment = [" + sql + "]");
        assertThat(sql).contains("user_id");
    }

    @Test
    void inheritedDeletedInLambdaResolves() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Record.class);

        LambdaQueryWrapper<Record> wrapper = new LambdaQueryWrapper<Record>()
                .eq(Record::getDeleted, 0);

        String sql = wrapper.getSqlSegment();
        System.out.println("sqlSegment = [" + sql + "]");
        assertThat(sql).contains("deleted");
    }

    @Test
    void test01() {
        String str = "111-";
        List<String> split = split(str, "-");
        System.out.println(split.size());
    }

    public static List<String> split(String value, String separator) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (StrUtil.isEmpty(separator)) {
            result.add(value);
            return result;
        }

        String[] values = value.split(Pattern.quote(separator), -1);
        for (String item : values) {
            result.add(item);
        }
        return result;
    }
}
