-- ============================================
-- sys_user 用户表
-- 数据库: saa_db
-- ============================================

CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`   VARCHAR(50)  NOT NULL COMMENT '登录用户名',
    `password`   VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
    `nickname`   VARCHAR(50)  DEFAULT NULL COMMENT '用户昵称',
    `avatar`     VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `status`     TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1=正常, 0=禁用',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
