-- ========================================
-- AI 记录助手 - 数据库初始化脚本
-- 数据库: MySQL 8.0+
-- ========================================

CREATE DATABASE IF NOT EXISTS xtx
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE xtx;

-- ========================================
-- 1. 用户表
-- ========================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT       NOT NULL                 COMMENT '主键(雪花ID)',
    `openid`      VARCHAR(64)  NOT NULL                 COMMENT '微信openid',
    `nickname`    VARCHAR(64)  DEFAULT NULL             COMMENT '昵称',
    `avatar_url`  VARCHAR(512) DEFAULT NULL             COMMENT '头像URL',
    `signature`   VARCHAR(128) DEFAULT NULL             COMMENT '个性签名',
    `email`       VARCHAR(128) DEFAULT NULL             COMMENT '邮箱',
    `location`    VARCHAR(64)  DEFAULT NULL             COMMENT '所在地(省·市·区纯文本)',
    `daily_quota` INT          NOT NULL DEFAULT 10      COMMENT '每日AI生成配额',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';


-- ========================================
-- 2. 记录表
-- ========================================
CREATE TABLE IF NOT EXISTS `record` (
    `id`          BIGINT       NOT NULL                 COMMENT '主键(雪花ID)',
    `user_id`     BIGINT       NOT NULL                 COMMENT '用户ID',
    `title`       VARCHAR(128) DEFAULT NULL             COMMENT '标题(空=无标题)',
    `label`       VARCHAR(64)  DEFAULT NULL             COMMENT '标签:dict_item.item_key(空=未分类)',
    `content`     TEXT         NOT NULL                 COMMENT '文字内容(可为空串，标题即笔记时正文为空)',
    `images`      JSON         DEFAULT NULL             COMMENT '图片objectKey数组(非URL,读时签发access URL)',
    `record_date` DATE         NOT NULL                 COMMENT '记录日期(支持补记)',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除标记(0-正常,1-删除)',
    `recycled_at` DATETIME     DEFAULT NULL             COMMENT '进回收站时间(NULL=正常,删除笔记时置值)',
    PRIMARY KEY (`id`),
    KEY `idx_user_record_date` (`user_id`, `record_date`, `deleted`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记录表';

-- 已存在的库：本脚本 CREATE TABLE IF NOT EXISTS 不会改已有表结构，需单独执行下面这行
-- ALTER TABLE `record` ADD COLUMN `title` VARCHAR(128) DEFAULT NULL COMMENT '标题(空=无标题)' AFTER `user_id`;
-- ALTER TABLE `record` ADD COLUMN `recycled_at` DATETIME DEFAULT NULL COMMENT '进回收站时间(NULL=正常,删除笔记时置值)' AFTER `deleted`;


-- ========================================
-- 3. 报告表
-- ========================================
CREATE TABLE IF NOT EXISTS `report`
(
    `id`           BIGINT       NOT NULL                COMMENT '主键(雪花ID)',
    `user_id`      BIGINT       NOT NULL                COMMENT '用户ID',
    `template`     VARCHAR(32)  NOT NULL                COMMENT '模板:DIARY/WEEKLY/STUDY_SUMMARY/REVIEW',
    `title`        VARCHAR(128) DEFAULT NULL            COMMENT '报告标题',
    `content`      LONGTEXT     DEFAULT NULL            COMMENT '报告内容(Markdown)',
    `start_date`   DATE         DEFAULT NULL            COMMENT '覆盖开始日期',
    `end_date`     DATE         DEFAULT NULL            COMMENT '覆盖结束日期',
    `category`     VARCHAR(16)  DEFAULT NULL            COMMENT '筛选分类:LIFE/STUDY/ALL',
    `record_count` INT          DEFAULT NULL            COMMENT '基于多少条记录生成',
    `model`        VARCHAR(64)  DEFAULT NULL            COMMENT '使用的模型名',
    `tokens_used`  INT          DEFAULT NULL            COMMENT '消耗token数',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0      COMMENT '逻辑删除标记(0-正常,1-删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`, `deleted`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告表';


-- ========================================
-- 4. 文件元数据表
--
-- 不继承 OwnedEntity：主键是 object_key 而非自增 id，且无 deleted 列
-- —— 孤儿清理任务要物理删行（连带删 MinIO 对象），逻辑删除会让已删对象的行永久滞留。
-- 因此归属校验在 FileServiceImpl 里手写，不走 OwnedServiceImpl。
-- ========================================
CREATE TABLE IF NOT EXISTS `file_metadata` (
    `object_key`        VARCHAR(256) NOT NULL                COMMENT '对象键(主键)',
    `user_id`           BIGINT       NOT NULL                COMMENT '用户ID',
    `original_filename` VARCHAR(256) DEFAULT NULL            COMMENT '原始文件名(下载时用于Content-Disposition)',
    `content_type`      VARCHAR(128) DEFAULT NULL            COMMENT '内容类型',
    `size`              BIGINT       DEFAULT NULL            COMMENT '文件大小(字节)',
    `status`            VARCHAR(16)  NOT NULL DEFAULT 'TEMP' COMMENT '状态:TEMP/ATTACHED/DETACHED',
    `record_id`         BIGINT       DEFAULT NULL            COMMENT '关联记录ID(ATTACHED时非空)',
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    `attached_at`       DATETIME     DEFAULT NULL            COMMENT '附加时间',
    PRIMARY KEY (`object_key`),
    KEY `idx_user_status_created` (`user_id`, `status`, `created_at`),
    KEY `idx_record_id` (`record_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件元数据表';


-- ========================================
-- 5. 字典类型表（类型目录：全项目有哪些字典）
--
-- 系统级共享数据，不继承 OwnedEntity：无 user_id、无 deleted。
-- 删除语义由 enabled 开关承担（只禁用、不硬删），故不需要逻辑删除列。
-- ========================================
CREATE TABLE IF NOT EXISTS `dict_type` (
    `id`         BIGINT       NOT NULL                 COMMENT '主键(雪花ID)',
    `type_code`  VARCHAR(64)  NOT NULL                 COMMENT '类型码，如 note_label',
    `type_name`  VARCHAR(128) NOT NULL                 COMMENT '类型名称，如 笔记标签',
    `remark`     VARCHAR(255) DEFAULT NULL             COMMENT '备注',
    `enabled`    TINYINT      NOT NULL DEFAULT 1       COMMENT '整本字典启用(0-否,1-是)',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型表';


-- ========================================
-- 6. 字典项表（字典内容：每本里有哪些项）
--
-- dict_type 列存 dict_type.type_code 字符串（非数字 id）：查某本字典的项不用 join，
-- 代价是 type_code 一旦被引用就不可改名（靠约定与 SQL 维护自觉遵守）。
-- extra 存类型专属属性（如标签颜色），不占通用列。
-- ========================================
CREATE TABLE IF NOT EXISTS `dict_item` (
    `id`         BIGINT       NOT NULL                 COMMENT '主键(雪花ID)',
    `dict_type`  VARCHAR(64)  NOT NULL                 COMMENT '所属字典类型码(= dict_type.type_code)',
    `item_key`   VARCHAR(64)  NOT NULL                 COMMENT '项键(机器值，业务表引用它)',
    `item_label` VARCHAR(128) NOT NULL                 COMMENT '展示名',
    `sort_order` INT          NOT NULL DEFAULT 0       COMMENT '排序(升序)',
    `enabled`    TINYINT      NOT NULL DEFAULT 1       COMMENT '启用(0-否,1-是)',
    `remark`     VARCHAR(255) DEFAULT NULL             COMMENT '备注',
    `extra`      JSON         DEFAULT NULL             COMMENT '类型专属属性，如 {"color":"#7C3AED"}',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_type_key` (`dict_type`, `item_key`),
    KEY `idx_type_enabled_sort` (`dict_type`, `enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项表';


-- ========================================
-- 7. 字典 seed：首个字典类型 note_label（笔记标签）
--
-- "未分类"不是字典项 —— 它是 record.label 为 NULL 的状态，故这里只有 4 项。
-- ========================================
INSERT INTO `dict_type` (`id`, `type_code`, `type_name`, `remark`) VALUES
    (1, 'note_label', '笔记标签', '笔记(record)的分类标签')
ON DUPLICATE KEY UPDATE `type_name` = VALUES(`type_name`);

-- extra 存 { color: { light, dark } }：暗色模式品牌色必须提亮（见 light-note global.scss 的 --brand-500 注释），
-- 前端 readTagColor 按当前主题取一套，缺省回落令牌。
INSERT INTO `dict_item` (`id`, `dict_type`, `item_key`, `item_label`, `sort_order`, `extra`) VALUES
    (1, 'note_label', 'work',   '工作', 10, JSON_OBJECT('color', JSON_OBJECT('light', '#7C3AED', 'dark', '#A98BFF'))),
    (2, 'note_label', 'design', '设计', 20, JSON_OBJECT('color', JSON_OBJECT('light', '#EC4899', 'dark', '#F68EC2'))),
    (3, 'note_label', 'tech',   '技术', 30, JSON_OBJECT('color', JSON_OBJECT('light', '#3B82F6', 'dark', '#8AB5F9'))),
    (4, 'note_label', 'life',   '生活', 40, JSON_OBJECT('color', JSON_OBJECT('light', '#F59E0B', 'dark', '#FBBF24')))
ON DUPLICATE KEY UPDATE `item_label` = VALUES(`item_label`), `extra` = VALUES(`extra`);
