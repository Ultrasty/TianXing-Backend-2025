-- 天行平台 管理员用户信息表
CREATE TABLE IF NOT EXISTS `sys_user` (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `user_name` varchar(64) NOT NULL COMMENT '用户账号',
  `nick_name` varchar(64) DEFAULT '' COMMENT '用户昵称',
  `password` varchar(100) NOT NULL DEFAULT '' COMMENT '密码',
  `status` char(1) DEFAULT '0' COMMENT '帐号状态（0正常 1停用）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_name` (`user_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户信息表';

-- 插入默认超级管理员 admin / admin123 (RuoYi-Vue 官方标准 60 位 BCrypt 密文)
INSERT INTO `sys_user` (`user_id`, `user_name`, `nick_name`, `password`, `status`, `create_time`)
VALUES (1, 'admin', '天行管理员', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', NOW())
ON DUPLICATE KEY UPDATE `password` = VALUES(`password`), `status` = '0';
