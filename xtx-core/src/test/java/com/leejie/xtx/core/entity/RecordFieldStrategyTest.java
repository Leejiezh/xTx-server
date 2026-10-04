package com.leejie.xtx.core.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 钉住 Record.label 的更新策略：NULL=未分类，必须"始终参与更新"（ALWAYS），
 * 否则用户把标签改回未分类时 label=null 会被默认 NOT_NULL 策略静默跳过、旧标签删不掉。
 *
 * <p>images 相反：null 表示"未提交该字段"（见 RecordServiceImpl 注释），必须保持默认策略。
 */
class RecordFieldStrategyTest {

    private static TableField tableField(String name) throws NoSuchFieldException {
        Field f = Record.class.getDeclaredField(name);
        return f.getAnnotation(TableField.class);
    }

    @Test
    @DisplayName("label 使用 ALWAYS，保证 label=null 能真正写库")
    void labelUsesAlwaysStrategy() throws Exception {
        TableField tf = tableField("label");
        assertNotNull(tf, "label 必须带 @TableField(updateStrategy = ALWAYS)");
        assertEquals(FieldStrategy.ALWAYS, tf.updateStrategy());
    }

    @Test
    @DisplayName("images 不得使用 ALWAYS（null 表示未提交该字段）")
    void imagesKeepsDefaultStrategy() throws Exception {
        TableField tf = tableField("images");
        assertNotNull(tf);
        assertEquals(FieldStrategy.DEFAULT, tf.updateStrategy());
    }
}
