package com.leejie.xtx.core.service;

import com.leejie.xtx.core.entity.DictItem;

import java.util.List;

/**
 * 通用字典读服务。
 *
 * <p>字典是系统级共享数据，不属于任何用户，因此不继承 OwnedService、没有 userId 概念。
 */
public interface DictService {

    /** 取某本字典的启用项，按 sort_order 升序；类型不存在或整本停用时返回空列表 */
    List<DictItem> listEnabledItems(String typeCode);
}
