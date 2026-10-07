package com.leejie.xtx.api.controller;

import cn.hutool.core.util.StrUtil;
import com.leejie.xtx.common.base.vo.PageResult;
import com.leejie.xtx.common.result.R;
import com.leejie.xtx.core.dto.SearchQuery;
import com.leejie.xtx.core.dto.SearchVO;
import com.leejie.xtx.core.entity.Record;
import com.leejie.xtx.core.service.RecordService;
import com.leejie.xtx.core.utils.SearchHighlightUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 笔记搜索：标题 + 正文子串匹配，返回服务端生成的高亮。
 *
 * <p>搜索是「只读查询」的一个正交维度，独立控制器便于后续演进（如命中算法、
 * 排序、拼音分词），不污染记录 CRUD 的入参。复用 {@link RecordService#search}。
 */
@Tag(name = "笔记搜索")
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final RecordService recordService;

    @GetMapping
    @Operation(summary = "分页搜索笔记（标题与正文，返回服务端高亮）")
    public R<PageResult<SearchVO>> search(@Valid SearchQuery query) {
        // 传给高亮生成的关键词与 service 的过滤口径一致（trim 后），避免空格导致高亮落空
        String kw = StrUtil.trimToNull(query.getQ());
        return R.ok(PageResult.of(recordService.search(query), r -> toVO(r, kw)));
    }

    /** 搜索项不含 content/images（结果卡片只展示标题+摘要，也省一次图片换签） */
    private SearchVO toVO(Record r, String keyword) {
        String kw = keyword == null ? "" : keyword;
        String excerpt = SearchHighlightUtil.excerpt(r.getContent(), kw);
        SearchVO vo = new SearchVO();
        vo.setId(r.getId());
        vo.setTitle(r.getTitle());
        vo.setLabel(r.getLabel());
        vo.setRecordDate(r.getRecordDate());
        vo.setCreatedAt(r.getCreatedAt());
        vo.setUpdatedAt(r.getUpdatedAt());
        vo.setExcerpt(excerpt);
        SearchVO.SearchHighlights hl = new SearchVO.SearchHighlights();
        hl.setTitle(SearchHighlightUtil.highlight(r.getTitle(), kw));
        hl.setExcerpt(SearchHighlightUtil.highlight(excerpt, kw));
        vo.setHighlights(hl);
        return vo;
    }
}
