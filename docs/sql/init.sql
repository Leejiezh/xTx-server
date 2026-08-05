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
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `openid`      VARCHAR(64)  NOT NULL                 COMMENT '微信openid',
    `nickname`    VARCHAR(64)  DEFAULT NULL             COMMENT '昵称',
    `avatar_url`  VARCHAR(512) DEFAULT NULL             COMMENT '头像URL',
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
    `id`          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `user_id`     BIGINT       NOT NULL                 COMMENT '用户ID',
    `category`    VARCHAR(16)  NOT NULL                 COMMENT '分类:LIFE/STUDY',
    `content`     TEXT         NOT NULL                 COMMENT '文字内容',
    `images`      JSON         DEFAULT NULL             COMMENT '图片URL数组',
    `record_date` DATE         NOT NULL                 COMMENT '记录日期(支持补记)',
    `source`      VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源:MANUAL/IMAGE',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`     TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除标记(0-正常,1-删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user_record_date` (`user_id`, `record_date`, `deleted`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='记录表';


-- ========================================
-- 3. 报告表
-- ========================================
CREATE TABLE IF NOT EXISTS `report` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `user_id`      BIGINT       NOT NULL                 COMMENT '用户ID',
    `template`     VARCHAR(32)  NOT NULL                 COMMENT '模板:DIARY/WEEKLY/STUDY_SUMMARY/REVIEW',
    `title`        VARCHAR(128) DEFAULT NULL             COMMENT '报告标题',
    `content`      LONGTEXT     DEFAULT NULL             COMMENT '报告内容(Markdown)',
    `start_date`   DATE         DEFAULT NULL             COMMENT '覆盖开始日期',
    `end_date`     DATE         DEFAULT NULL             COMMENT '覆盖结束日期',
    `category`     VARCHAR(16)  DEFAULT NULL             COMMENT '筛选分类:LIFE/STUDY/ALL',
    `record_count` INT          DEFAULT NULL             COMMENT '基于多少条记录生成',
    `model`        VARCHAR(64)  DEFAULT NULL             COMMENT '使用的模型名',
    `tokens_used`  INT          DEFAULT NULL             COMMENT '消耗token数',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`      TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除标记(0-正常,1-删除)',
    PRIMARY KEY (`id`),
    KEY `idx_user_created` (`user_id`, `created_at`, `deleted`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报告表';