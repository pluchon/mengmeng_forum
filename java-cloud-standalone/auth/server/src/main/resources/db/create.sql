-- auth 域最终空库基线，已合并历史增量；本文件不会删除已有数据库或表。
-- 仅对全新空库执行；已有表时应失败并改用经过审核的前向迁移。

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `forum_auth_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `forum_auth_db`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dept` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门ID',
  `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父级ID，0为根',
  `name` varchar(100) NOT NULL COMMENT '部门名称',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `status` char(1) NOT NULL DEFAULT '1' COMMENT '状态 1启用 0停用',
  `description` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台部门树';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_data` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_code` varchar(64) NOT NULL COMMENT '字典编码',
  `label` varchar(100) NOT NULL COMMENT '显示文本',
  `value` varchar(100) NOT NULL COMMENT '值',
  `sort` int NOT NULL DEFAULT '0',
  `status` char(1) NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  PRIMARY KEY (`id`),
  KEY `idx_dict_code` (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典数据';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dict_code` varchar(64) NOT NULL COMMENT '字典编码',
  `dict_name` varchar(100) NOT NULL COMMENT '字典名称',
  `remark` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_code` (`dict_code`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='字典类型';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_menu` (
  `id` varchar(32) NOT NULL COMMENT '菜单ID（与前端路由组件路径约定一致）',
  `parent_id` varchar(32) NOT NULL DEFAULT '' COMMENT '父菜单ID',
  `path` varchar(200) NOT NULL COMMENT '路由 path',
  `component` varchar(200) DEFAULT NULL COMMENT '组件标识 Layout / system/user/index',
  `redirect` varchar(200) DEFAULT NULL COMMENT '重定向',
  `type` tinyint NOT NULL COMMENT '1目录 2菜单 3按钮',
  `title` varchar(100) NOT NULL COMMENT '标题',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `hidden` tinyint NOT NULL DEFAULT '0' COMMENT '是否隐藏',
  `keep_alive` tinyint NOT NULL DEFAULT '0' COMMENT '是否缓存',
  `breadcrumb` tinyint NOT NULL DEFAULT '1' COMMENT '面包屑',
  `affix` tinyint NOT NULL DEFAULT '0' COMMENT '固定标签',
  `show_in_tabs` tinyint NOT NULL DEFAULT '1' COMMENT '是否出现在标签栏',
  `always_show` tinyint NOT NULL DEFAULT '0' COMMENT '是否总是显示父级',
  `active_menu` varchar(200) DEFAULT NULL COMMENT '高亮菜单 path',
  `permission` varchar(100) DEFAULT NULL COMMENT '权限标识',
  `status` char(1) NOT NULL DEFAULT '1' COMMENT '状态',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台菜单';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_code` varchar(64) NOT NULL COMMENT '角色编码，如 role_admin',
  `role_name` varchar(100) NOT NULL COMMENT '角色名称',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='后台角色';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_role_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `menu_id` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_menu` (`role_id`,`menu_id`),
  KEY `idx_menu_id` (`menu_id`)
) ENGINE=InnoDB AUTO_INCREMENT=14 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-菜单权限';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user_role` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT 'forum.user.id',
  `role_id` bigint NOT NULL COMMENT 'sys_role.id',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户-角色';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户编号, 主键, 自增',
  `username` varchar(20) NOT NULL COMMENT '用户名, 非空, 唯一',
  `password` varchar(255) NOT NULL COMMENT 'BCrypt 哈希（约60字符）',
  `nickname` varchar(50) NOT NULL COMMENT '昵称, 非空',
  `phone_num` varchar(255) DEFAULT NULL COMMENT '手机号密文',
  `phone_hash` varchar(64) DEFAULT NULL COMMENT '手机号HMAC，用于等值查询',
  `email` varchar(255) DEFAULT NULL COMMENT '邮箱密文',
  `email_hash` varchar(64) DEFAULT NULL COMMENT '邮箱HMAC，用于等值查询',
  `gender` tinyint NOT NULL DEFAULT '2' COMMENT '0女 1男 2保密, 非空, 默认2',
  `salt` varchar(32) NOT NULL DEFAULT '' COMMENT '历史 MD5 盐；BCrypt 用户为空串',
  `avatar_url` varchar(255) DEFAULT NULL COMMENT '用户头像URL',
  `background_url` varchar(500) DEFAULT NULL COMMENT '用户主页背景图URL',
  `article_count` int NOT NULL DEFAULT '0' COMMENT '发帖数量',
  `is_admin` tinyint NOT NULL DEFAULT '0' COMMENT '是否管理员, 0否 1是',
  `vip_tier` tinyint NOT NULL DEFAULT '0' COMMENT 'VIP档位快照: 0普通 1PRO 2MAX（权威在 economy.user_vip_subscription）',
  `vip_expire_at` datetime DEFAULT NULL COMMENT 'VIP到期时间快照（权威在 economy.user_vip_subscription）',
  `creator_state` tinyint NOT NULL DEFAULT '0' COMMENT '创作者认证状态: 0未认证 1已认证',
  `mascot_model_id` bigint DEFAULT NULL COMMENT '看板娘模型快照（权威在 ai.user_mascot_preference）',
  `remark` varchar(1000) DEFAULT NULL COMMENT '备注, 自我介绍',
  `ip_region` varchar(32) DEFAULT NULL COMMENT '最近登录IP属地(省份/国家)',
  `dept_id` bigint DEFAULT NULL COMMENT '后台部门ID，对应 sys_dept.id',
  `state` tinyint NOT NULL DEFAULT '0' COMMENT '状态: 0正常, 1禁言',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_username_uindex` (`username`),
  UNIQUE KEY `user_phone_hash_uindex` (`phone_hash`),
  UNIQUE KEY `user_email_hash_uindex` (`email_hash`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_profile_change_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `field_type` varchar(20) NOT NULL COMMENT 'NICKNAME/BIO',
  `candidate_content` varchar(255) NOT NULL DEFAULT '' COMMENT '待审核内容',
  `content_hash` varchar(32) NOT NULL COMMENT '内容MD5',
  `review_status` tinyint NOT NULL DEFAULT '0' COMMENT '0待审核 1审核中 2通过 3拒绝 4失败 5被新申请替代',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '审核重试次数',
  `review_reason` varchar(500) DEFAULT NULL COMMENT '审核原因',
  `reviewed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_profile_change_user_field` (`user_id`,`field_type`,`delete_state`,`id`),
  KEY `idx_profile_change_retry` (`review_status`,`retry_count`,`update_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户资料异步审核申请';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_follow` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `follower_id` bigint NOT NULL,
  `followee_id` bigint NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_follower_followee` (`follower_id`,`followee_id`),
  KEY `idx_followee_id` (`followee_id`)
) ENGINE=InnoDB AUTO_INCREMENT=20 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户关注关系表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_login_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `login_type` varchar(16) NOT NULL COMMENT 'password/mail/sms',
  `ip_address` varchar(64) DEFAULT NULL COMMENT '登录IP',
  `user_agent` varchar(512) DEFAULT NULL COMMENT 'UA摘要',
  `login_status` tinyint NOT NULL DEFAULT '1' COMMENT '1成功 0失败',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_login_log_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=341 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户登录日志';
/*!40101 SET character_set_client = @saved_cs_client */;
