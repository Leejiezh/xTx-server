package com.leejie.xtx.core.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchHighlightUtilTest {

    // ---------- escapeHtml ----------

    @Test
    @DisplayName("转义 & < > \"，& 最先转")
    void escapeHtml_escapesSpecialChars() {
        assertEquals("&lt;b&gt; &amp; &quot;x&quot;", SearchHighlightUtil.escapeHtml("<b> & \"x\""));
    }

    @Test
    @DisplayName("null 转义为空串")
    void escapeHtml_null_returnsEmpty() {
        assertEquals("", SearchHighlightUtil.escapeHtml(null));
    }

    // ---------- excerpt（命中窗口摘要） ----------

    @Test
    @DisplayName("命中靠后时摘要围绕命中截取，前置省略号")
    void excerpt_centersOnFirstMatch() {
        String content = "这是一段足够长的正文，用来验证摘要会在命中位置附近截取窗口，而不是固定取开头。" +
                "设计规范这个词放在后面，前面铺垫足够多，确保命中位置超过上下文宽度，后面的内容也足够长到会被截断。";
        String s = SearchHighlightUtil.excerpt(content, "设计规范");

        assertTrue(s.startsWith("…"), "命中靠后应带前导省略号");
        assertTrue(s.contains("设计规范"), "摘要应包含关键词");
        assertTrue(s.endsWith("…"), "正文尾部仍有内容应带后置省略号");
    }

    @Test
    @DisplayName("命中在开头时不带前导省略号")
    void excerpt_matchAtStart_noLeadingEllipsis() {
        String content = "设计规范是第一条笔记的内容。";
        assertEquals(content, SearchHighlightUtil.excerpt(content, "设计规范"));
    }

    @Test
    @DisplayName("正文未命中时退化为开头窗口")
    void excerpt_noMatch_returnsHeadWindow() {
        assertEquals("a".repeat(60) + "…", SearchHighlightUtil.excerpt("a".repeat(80), "zzz"));
    }

    @Test
    @DisplayName("空白正文返回空串")
    void excerpt_blankContent_returnsEmpty() {
        assertEquals("", SearchHighlightUtil.excerpt("  ", "x"));
        assertEquals("", SearchHighlightUtil.excerpt(null, "x"));
    }

    // ---------- highlight（转义 + 高亮） ----------

    @Test
    @DisplayName("命中段包 span，非命中段按原样")
    void highlight_wrapsMatches() {
        assertEquals("这是<span class=\"hl\">设计</span>规范",
                SearchHighlightUtil.highlight("这是设计规范", "设计"));
    }

    @Test
    @DisplayName("不区分大小写命中")
    void highlight_caseInsensitive() {
        assertEquals("<span class=\"hl\">MySQL</span> 笔记",
                SearchHighlightUtil.highlight("MySQL 笔记", "mysql"));
    }

    @Test
    @DisplayName("先转义后高亮，防止注入")
    void highlight_escapesHtmlBeforeWrap() {
        assertEquals("&lt;script&gt;<span class=\"hl\">alert</span>(1)&lt;/script&gt;",
                SearchHighlightUtil.highlight("<script>alert(1)</script>", "alert"));
    }

    @Test
    @DisplayName("关键词含正则元字符时按字面量匹配")
    void highlight_keywordWithRegexChars_literal() {
        assertEquals("价格 <span class=\"hl\">1+1</span>=2",
                SearchHighlightUtil.highlight("价格 1+1=2", "1+1"));
    }

    @Test
    @DisplayName("纯字母关键词不会拆坏正文里的 HTML 实体")
    void highlight_asciiKeyword_doesNotBreakEntities() {
        String out = SearchHighlightUtil.highlight("&lt;b&gt;", "lt");
        assertTrue(out.contains("<span class=\"hl\">lt</span>"), "应命中正文里的字面 lt");
        assertFalse(out.contains("&<span"), "不得在实体内部插入 span（&lt; 应先转成 &amp;lt;）");
    }

    @Test
    @DisplayName("空关键词返回转义后的纯文本")
    void highlight_emptyKeyword_returnsEscaped() {
        assertEquals("&lt;b&gt;", SearchHighlightUtil.highlight("<b>", ""));
    }
}
