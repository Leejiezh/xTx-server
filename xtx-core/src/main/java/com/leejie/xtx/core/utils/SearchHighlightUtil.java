package com.leejie.xtx.core.utils;

import java.util.Locale;

/**
 * 搜索摘要与高亮工具。
 *
 * <p>高亮链路与前端 mock 对齐：<b>先转义原文，再包 &lt;span class="hl"&gt;</b> ——
 * 转义的是关键词而非正文，否则正则会把全文都包进高亮；且只产出这一个标签，
 * rich-text 解析不出别的 HTML，天然防注入。
 */
public final class SearchHighlightUtil {

    /** 摘要目标长度（字符） */
    private static final int SNIPPET_LEN = 60;
    /** 命中前保留的上下文宽度（字符），命中太靠后时摘要仍能看到关键词 */
    private static final int LEAD_LEN = 20;

    private SearchHighlightUtil() {
    }

    /**
     * HTML 转义：&amp; &lt; &gt; &quot;（顺序与前端 mock 一致，& 必须最先转）。
     */
    public static String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 围绕首个命中的纯文本摘要：关键词在正文靠后时也能看到上下文，而不是固定取开头。
     * 未命中（只命中标题）时退化为开头窗口；正文空返回空串。
     */
    public static String excerpt(String content, String keyword) {
        String text = content == null ? "" : content;
        if (text.isBlank()) {
            return "";
        }
        String kw = keyword == null ? "" : keyword;
        int idx = text.toLowerCase(Locale.ROOT).indexOf(kw.toLowerCase(Locale.ROOT));
        if (idx < 0) {
            // 只命中标题或未命中：退化为开头窗口
            return truncate(text, 0, SNIPPET_LEN);
        }
        int start = Math.max(0, idx - LEAD_LEN);
        return truncate(text, start, Math.max(idx + kw.length(), start + SNIPPET_LEN));
    }

    /**
     * 高亮：对<b>原始文本</b>按关键词不区分大小写分段，逐段转义后再把命中段包进
     * <span class="hl">。在原文上分段而非在转义文本上正则匹配 —— 否则关键词是
     * 纯字母（如 lt）时会误命中 HTML 实体里的小写片段，产出坏掉的 HTML。
     * 未命中关键词时返回转义后的纯文本（不返回 null，前端可省掉空值分支）。
     */
    public static String highlight(String text, String keyword) {
        String raw = text == null ? "" : text;
        String kw = keyword == null ? "" : keyword;
        if (kw.isEmpty()) {
            return escapeHtml(raw);
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        String lowerKw = kw.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        int from = 0;
        int idx = lower.indexOf(lowerKw);
        while (idx >= 0) {
            sb.append(escapeHtml(raw.substring(from, idx)));
            sb.append("<span class=\"hl\">")
                    .append(escapeHtml(raw.substring(idx, idx + kw.length())))
                    .append("</span>");
            from = idx + kw.length();
            idx = lower.indexOf(lowerKw, from);
        }
        return sb.append(escapeHtml(raw.substring(from))).toString();
    }

    /** 截取 [start, end) 窗口，越界处补 … */
    private static String truncate(String text, int start, int end) {
        int from = Math.min(start, text.length());
        int to = Math.min(Math.max(end, from), text.length());
        StringBuilder sb = new StringBuilder();
        if (from > 0) {
            sb.append('…');
        }
        sb.append(text, from, to);
        if (to < text.length()) {
            sb.append('…');
        }
        return sb.toString();
    }
}
