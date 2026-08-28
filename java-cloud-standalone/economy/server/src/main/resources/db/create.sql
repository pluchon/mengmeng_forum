-- economy 域最终空库基线，已合并历史增量；本文件不会删除已有数据库或表。
-- 仅对全新空库执行；已有表时应失败并改用经过审核的前向迁移。

-- 必须先声明会话字符集：下方 mysqldump 风格的 character_set_client 只在每个 CREATE TABLE
-- 前后成对生效，到 seed INSERT 时已还原成客户端默认值。若客户端默认是 latin1，
-- 所有中文初始化数据会被按 latin1 写入而变成乱码。
SET NAMES utf8mb4;

CREATE DATABASE /*!32312 IF NOT EXISTS*/ `forum_economy_db` /*!40100 DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci */ /*!80016 DEFAULT ENCRYPTION='N' */;

USE `forum_economy_db`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `checkin_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `checkin_date` date NOT NULL COMMENT '签到日期',
  `points` int NOT NULL DEFAULT '0' COMMENT '本次签到获得基础积分',
  `bonus_points` int NOT NULL DEFAULT '0' COMMENT '本次连续签到额外奖励积分',
  `streak_days` int NOT NULL DEFAULT '0' COMMENT '签到时的连续天数快照',
  `is_makeup` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否补签: 0否 1是',
  `surprise_type` varchar(32) DEFAULT NULL COMMENT '惊喜奖励类型',
  `surprise_value` int DEFAULT NULL COMMENT '惊喜奖励数值',
  `surprise_label` varchar(64) DEFAULT NULL COMMENT '惊喜奖励展示文案',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_date` (`user_id`,`checkin_date`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=25 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到流水表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `checkin_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `month` tinyint NOT NULL DEFAULT '0' COMMENT '月份, 0表示默认规则, 1-12表示具体月份',
  `day_number` tinyint NOT NULL COMMENT '当月第几天, 1-31',
  `points` int NOT NULL DEFAULT '0' COMMENT '签到获得积分(默认规则按日配置, 建议 30-50)',
  `is_surprise` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否惊喜奖励日: 0否 1是',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_month_day` (`month`,`day_number`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签到积分规则表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `checkin_streak_reward` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `streak_days` int NOT NULL COMMENT '连续签到天数门槛',
  `reward_type` varchar(32) NOT NULL DEFAULT 'POINTS' COMMENT '奖励类型: POINTS/STARLIGHT/MAKEUP_CARD/MIXED',
  `bonus_points` int NOT NULL DEFAULT '0' COMMENT '额外奖励积分',
  `starlight_amount` int NOT NULL DEFAULT '0' COMMENT '发放萌星辉数量',
  `makeup_card_amount` int NOT NULL DEFAULT '0' COMMENT '发放补签卡数量',
  `vip_days` int NOT NULL DEFAULT '0' COMMENT '发放 VIP 体验天数',
  `title` varchar(64) DEFAULT NULL COMMENT '前端主文案',
  `subtitle` varchar(128) DEFAULT NULL COMMENT '前端副文案',
  `description` varchar(100) DEFAULT NULL COMMENT '奖励描述',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_streak_days` (`streak_days`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连续签到奖励表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `checkin_grant_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `grant_kind` varchar(32) NOT NULL COMMENT '发奖种类: STREAK/SURPRISE/MAKEUP',
  `biz_key` varchar(128) NOT NULL COMMENT '业务幂等键',
  `reward_type` varchar(32) DEFAULT NULL COMMENT '奖励类型快照',
  `reward_value` int DEFAULT NULL COMMENT '奖励数值快照',
  `reward_label` varchar(128) DEFAULT NULL COMMENT '奖励文案快照',
  `related_id` bigint DEFAULT NULL COMMENT '关联签到流水 ID',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkin_grant_user_kind_key` (`user_id`,`grant_kind`,`biz_key`),
  KEY `idx_checkin_grant_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到发奖幂等流水';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `checkin_surprise_pool` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `reward_type` varchar(32) NOT NULL COMMENT '奖励类型: POINTS/VIP_DAYS/STARLIGHT/MAKEUP_CARD/LOTTERY_VOUCHER',
  `reward_value` int NOT NULL DEFAULT '0' COMMENT '奖励数值',
  `weight` int NOT NULL DEFAULT '1' COMMENT '抽取权重',
  `label` varchar(64) NOT NULL COMMENT '展示文案',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_checkin_surprise_pool_sort` (`delete_state`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='签到惊喜奖池';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `emoji_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `shop_id` bigint NOT NULL COMMENT '所属商品ID',
  `image_url` varchar(500) NOT NULL COMMENT '表情图片URL',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序(升序), 默认 0',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_shop_id` (`shop_id`)
) ENGINE=InnoDB AUTO_INCREMENT=31 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表情包图片表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `emoji_shop` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '商品ID',
  `name` varchar(20) NOT NULL COMMENT '表情包名称(1-20字)',
  `description` varchar(50) DEFAULT NULL COMMENT '表情包说明(上传者填写, 1-50字)',
  `category` varchar(16) NOT NULL DEFAULT 'OTHER' COMMENT '分类: MOE/ONEE_SAN/REPOST/MEME/OTHER',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面预览图URL，草稿可为空',
  `price` int NOT NULL DEFAULT '0' COMMENT '售价积分',
  `upload_user_id` bigint DEFAULT NULL COMMENT '上传者ID, NULL 表示站长推荐',
  `sales_count` int NOT NULL DEFAULT '0' COMMENT '销售数量',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0待审核 1上架 2下架 3草稿',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_upload_user_id` (`upload_user_id`),
  KEY `idx_status_sales` (`status`,`sales_count`),
  KEY `idx_status_category_sales` (`status`,`category`,`sales_count`),
  KEY `idx_upload_status_update` (`upload_user_id`,`status`,`update_time`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表情包商品表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题目ID',
  `bank_id` bigint NOT NULL COMMENT '题库ID',
  `question_order` int NOT NULL COMMENT '题目顺序',
  `source_no` varchar(30) DEFAULT NULL COMMENT '原文题号',
  `section_name` varchar(100) DEFAULT NULL COMMENT '原文章节或分组',
  `question_type` varchar(20) NOT NULL COMMENT '题目类型',
  `stem` text NOT NULL COMMENT '题干',
  `options_json` json DEFAULT NULL COMMENT '选项JSON',
  `standard_answer` text COMMENT '标准答案',
  `explanation` text COMMENT '解析',
  `answer_inferred_from_user` tinyint NOT NULL DEFAULT '0' COMMENT '答案是否从用户答案推断',
  `needs_option_review` tinyint NOT NULL DEFAULT '0' COMMENT '是否需要人工复核选项',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_bank_order` (`bank_id`,`delete_state`,`question_order`)
) ENGINE=InnoDB AUTO_INCREMENT=1704 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试题库题目表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_question_bank` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '题库ID',
  `user_id` bigint NOT NULL COMMENT '创建用户ID',
  `subject` varchar(100) NOT NULL COMMENT '考试科目',
  `source_name` varchar(255) NOT NULL COMMENT '来源文件名',
  `total_count` int NOT NULL DEFAULT '0' COMMENT '总题数',
  `choice_count` int NOT NULL DEFAULT '0' COMMENT '选择题数量',
  `judgement_count` int NOT NULL DEFAULT '0' COMMENT '判断题数量',
  `subjective_count` int NOT NULL DEFAULT '0' COMMENT '主观题数量',
  `warnings_json` json DEFAULT NULL COMMENT '解析警告JSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0否 1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_subject_time` (`user_id`,`subject`,`delete_state`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试题库主表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_question_user_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '进度ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `bank_id` bigint NOT NULL COMMENT '题库ID',
  `question_id` bigint NOT NULL COMMENT '题目ID',
  `answer_text` varchar(1000) DEFAULT NULL COMMENT '用户答案',
  `answered` tinyint NOT NULL DEFAULT '0' COMMENT '是否已作答: 0否 1是',
  `correct` tinyint DEFAULT NULL COMMENT '是否答对: 0否 1是',
  `wrong` tinyint NOT NULL DEFAULT '0' COMMENT '是否错题: 0否 1是',
  `focus` tinyint NOT NULL DEFAULT '0' COMMENT '是否重点记忆: 0否 1是',
  `judge_score` int DEFAULT NULL COMMENT '主观题评分',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_progress_user_question` (`user_id`,`question_id`),
  KEY `idx_exam_progress_user_bank` (`user_id`,`bank_id`,`delete_state`),
  KEY `idx_exam_progress_user_focus` (`user_id`,`focus`,`delete_state`),
  KEY `idx_exam_progress_user_wrong` (`user_id`,`wrong`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='考试题库用户答题进度表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_activity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '活动ID',
  `title` varchar(100) NOT NULL COMMENT '活动标题',
  `description` varchar(2000) DEFAULT NULL COMMENT '活动说明(可较长)',
  `cover_image_url` varchar(512) DEFAULT NULL COMMENT '活动封面相对路径(OSS)',
  `publisher_id` bigint DEFAULT NULL COMMENT '创建人(管理员)用户ID',
  `cost_points_per_draw` int NOT NULL DEFAULT '30' COMMENT '单次抽奖消耗积分',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1对用户端开放 0关闭',
  `phase` tinyint NOT NULL DEFAULT '0' COMMENT '0筹划中 1进行中 2已截止',
  `start_time` datetime DEFAULT NULL COMMENT '开始时间(可空)',
  `end_time` datetime DEFAULT NULL COMMENT '结束时间(可空)',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_lottery_act_phase` (`phase`,`delete_state`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖活动表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_activity_prize` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `prize_id` bigint NOT NULL COMMENT '奖品ID',
  `weight` int NOT NULL DEFAULT '1' COMMENT '权重',
  `stock_remaining` int NOT NULL DEFAULT '-1' COMMENT '剩余库存,-1不限量',
  `is_jackpot` tinyint NOT NULL DEFAULT '0' COMMENT '是否头奖(大奖动效)',
  `image_path` varchar(512) DEFAULT NULL COMMENT '本活动该行奖品图(可空则回落 lottery_prize.image_path)',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='活动奖品关联表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_draw_hourly_stat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `stat_hour` datetime NOT NULL COMMENT '东八区整点(如 2026-05-12 14:00:00)',
  `draw_count` int NOT NULL DEFAULT '0' COMMENT '该小时抽奖次数(单抽+十连每抽计1)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_act_hour` (`activity_id`,`stat_hour`),
  KEY `idx_lottery_hour_act` (`activity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=133 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖活动按小时参与次数';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_draw_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `activity_prize_id` bigint NOT NULL COMMENT '活动奖品关联ID',
  `prize_id` bigint NOT NULL COMMENT '奖品ID',
  `prize_name` varchar(100) NOT NULL COMMENT '奖品名称快照',
  `prize_type` tinyint NOT NULL COMMENT '奖品类型快照',
  `prize_value` int NOT NULL DEFAULT '0' COMMENT '奖品数值快照',
  `grant_points` int NOT NULL DEFAULT '0' COMMENT '本次发放积分(仅积分奖)',
  `is_jackpot` tinyint NOT NULL DEFAULT '0' COMMENT '是否头奖快照',
  `mystery_item_type` tinyint DEFAULT NULL COMMENT '神秘子项类型 4积分/5VIP天',
  `mystery_item_value` int DEFAULT NULL COMMENT '神秘子项数值',
  `grant_vip_tier` tinyint DEFAULT NULL COMMENT 'VIP天奖项实发档位: 1PRO 2MAX; 空按PRO',
  `draw_batch_key` varchar(40) DEFAULT NULL COMMENT '十连批次UUID',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=142 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖记录表（每次开奖一条；十连共10条，同 batch）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_draw_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '活动ID',
  `request_id` varchar(64) NOT NULL COMMENT '客户端幂等键',
  `times` int NOT NULL COMMENT '抽奖次数 1或10',
  `batch_key` varchar(40) DEFAULT NULL COMMENT '批次键，关联 lottery_draw_record.draw_batch_key',
  `pity_after` int DEFAULT NULL COMMENT '批次结束后的硬保底计数',
  `vouchers_used` int DEFAULT NULL COMMENT '本批使用抵扣券数量，NULL表示历史未记录',
  `points_charged` int DEFAULT NULL COMMENT '本批实际扣除萌币，NULL表示历史未记录',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_user_request` (`user_id`,`request_id`),
  KEY `idx_lottery_request_batch` (`batch_key`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖请求幂等表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_prize` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '奖品ID',
  `name` varchar(100) NOT NULL COMMENT '奖品名称',
  `prize_type` tinyint NOT NULL COMMENT '0谢谢 1大奖 2小奖 3安慰奖 4积分 5VIP天',
  `prize_value` int NOT NULL DEFAULT '0' COMMENT '积分额或VIP天数,其它为0',
  `stock_quantity` int NOT NULL DEFAULT '-1' COMMENT '奖品库库存,-1表示不限量',
  `catalog_status` tinyint NOT NULL DEFAULT '1' COMMENT '0草稿 1上架 2下架',
  `is_mystery_bundle` tinyint NOT NULL DEFAULT '0' COMMENT '1=神秘大奖(多维子项开奖)',
  `image_path` varchar(512) DEFAULT NULL COMMENT '奖品图相对路径 forum_db_item/forum_prize_picture/...',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_lottery_prize_cat` (`catalog_status`,`delete_state`,`prize_type`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖奖品表(奖品库+活动引用)';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_prize_mystery_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `prize_id` bigint NOT NULL COMMENT '父奖品 lottery_prize.id',
  `item_type` tinyint NOT NULL COMMENT '4积分 5VIP天',
  `item_value` int NOT NULL COMMENT '积分数或VIP天数',
  `vip_tier` tinyint DEFAULT NULL COMMENT 'VIP天奖项的发放档位: 1PRO 2MAX; 空按PRO',
  `weight` int NOT NULL DEFAULT '1' COMMENT '开奖权重',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mystery_prize` (`prize_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='神秘大奖子奖项';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `delta` int NOT NULL COMMENT '本次变动量(正数入账, 负数消费)',
  `balance_after` int NOT NULL COMMENT '变动后余额快照',
  `source_type` tinyint NOT NULL COMMENT '来源: 0签到基础 1连签奖励 2商城 3退款 4抽奖消耗 5抽奖奖励 6注册 7VIP订阅 8抽奖彩蛋 9AI陪伴 10AI生图 99管理员',
  `related_id` bigint DEFAULT NULL COMMENT '关联业务行ID(可空)',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '业务幂等键，一次性变动必填',
  `remark` varchar(200) DEFAULT NULL COMMENT '人类可读描述',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_user_idempotency` (`user_id`,`idempotency_key`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_user_source` (`user_id`,`source_type`)
) ENGINE=InnoDB AUTO_INCREMENT=181 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='积分钱包流水表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID（逻辑关联 auth.user，无跨库外键）',
  `balance` int NOT NULL DEFAULT '0' COMMENT '当前积分余额',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_wallet_user_id` (`user_id`),
  KEY `idx_points_wallet_delete_state` (`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分钱包（economy 权威）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `points_milestone_claim` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `milestone_code` varchar(16) NOT NULL COMMENT '里程碑编码',
  `reward_amount` int NOT NULL COMMENT '领取奖励数量',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除：0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_milestone_claim_user_code` (`user_id`,`milestone_code`),
  KEY `idx_points_milestone_claim_user` (`user_id`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='萌币里程碑领取记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_checkin_info` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号, 主键, 自增',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `total_days` int NOT NULL DEFAULT '0' COMMENT '累计签到天数',
  `streak_days` int NOT NULL DEFAULT '0' COMMENT '当前连续签到天数',
  `total_points` int NOT NULL DEFAULT '0' COMMENT '签到累计获得积分',
  `last_checkin` date DEFAULT NULL COMMENT '最后一次签到日期',
  `makeup_card_count` int NOT NULL DEFAULT '0' COMMENT '持有补签卡数量',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户签到状态表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_emoji` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `shop_id` bigint NOT NULL COMMENT '购买的商品ID',
  `price_paid` int NOT NULL DEFAULT '0' COMMENT '实际支付积分(预留优惠券抵扣)',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购买时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_user_shop` (`user_id`,`shop_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户已购表情包表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_lottery_pity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `pity_draws` int NOT NULL DEFAULT '0' COMMENT '距上次神秘大奖已连续开奖次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_lottery_pity_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抽奖硬保底计数（economy 权威）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_lottery_voucher` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `balance` int NOT NULL DEFAULT '0' COMMENT '抵扣券余额',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_lottery_voucher_user_id` (`user_id`),
  KEY `idx_user_lottery_voucher_delete_state` (`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抽奖抵扣券钱包（economy 权威）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_voucher_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `delta` int NOT NULL COMMENT '变动量(正数发放, 负数抵扣)',
  `balance_after` int NOT NULL COMMENT '变动后券余额快照',
  `source_type` tinyint NOT NULL COMMENT '来源: 1任务发放 2抽奖抵扣 3收集册里程 4萌星辉商城 5签到惊喜',
  `related_id` bigint DEFAULT NULL COMMENT '关联业务行ID(可空)',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '业务幂等键',
  `remark` varchar(200) DEFAULT NULL COMMENT '人类可读描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_voucher_user_idempotency` (`user_id`,`idempotency_key`),
  KEY `idx_lottery_voucher_log_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='抽奖抵扣券流水（只增）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_pool_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` bigint NOT NULL COMMENT '活动/卡池ID',
  `task_code` varchar(40) NOT NULL COMMENT '任务编码 COMMENT_1/LIKE_3/CHECKIN_TODAY',
  `title` varchar(80) NOT NULL COMMENT '展示标题',
  `target_count` int NOT NULL DEFAULT '1' COMMENT '目标完成次数',
  `voucher_reward` int NOT NULL DEFAULT '1' COMMENT '奖励抵扣券张数',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示排序, 越小越靠前',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_pool_task_act_code` (`activity_id`,`task_code`),
  KEY `idx_lottery_pool_task_act` (`activity_id`,`enabled`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='卡池专属任务配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_lottery_task_claim` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '活动/卡池ID',
  `task_code` varchar(40) NOT NULL COMMENT '任务编码',
  `claim_date` date NOT NULL COMMENT '领取日期(上海时区日历日)',
  `voucher_granted` int NOT NULL DEFAULT '0' COMMENT '本次发放券数快照',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_lottery_task_claim` (`user_id`,`activity_id`,`task_code`,`claim_date`),
  KEY `idx_user_lottery_task_claim_user` (`user_id`,`claim_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='卡池任务每日领取记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_collect_milestone` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `threshold_count` int NOT NULL COMMENT '达成所需收集数',
  `reward_type` varchar(20) NOT NULL COMMENT '奖励: RANDOM/VOUCHER/POINTS/VIP_DAYS',
  `reward_value` int NOT NULL DEFAULT '0' COMMENT '主奖励数值(券张数/积分数/VIP天数)',
  `alt_reward_value` int DEFAULT NULL COMMENT 'RANDOM 备选积分数',
  `label` varchar(40) NOT NULL COMMENT '展示文案',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示排序',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1启用 0停用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_collect_ms_threshold` (`threshold_count`),
  KEY `idx_lottery_collect_ms_enabled` (`enabled`,`delete_state`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幸运收集册里程奖励配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_lottery_collect_owned` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '活动/卡池ID',
  `icon_id` int NOT NULL COMMENT '收集图标编号 1~80',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_lottery_collect_owned` (`user_id`,`activity_id`,`icon_id`),
  KEY `idx_user_lottery_collect_owned_user` (`user_id`,`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户幸运收集册已收集图标';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_lottery_collect_claim` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `activity_id` bigint NOT NULL COMMENT '活动/卡池ID',
  `threshold_count` int NOT NULL COMMENT '里程阈值',
  `reward_type` varchar(20) NOT NULL COMMENT '实际发放类型',
  `reward_value` int NOT NULL DEFAULT '0' COMMENT '实际发放数值',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_lottery_collect_claim` (`user_id`,`activity_id`,`threshold_count`),
  KEY `idx_user_lottery_collect_claim_user` (`user_id`,`activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户幸运收集册里程领取记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_starlight_wallet` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `balance` int NOT NULL DEFAULT '0' COMMENT '萌星辉余额',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_starlight_wallet_user_id` (`user_id`),
  KEY `idx_user_starlight_wallet_delete_state` (`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='萌星辉钱包（economy 权威）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `starlight_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `delta` int NOT NULL COMMENT '变动量(正数发放, 负数消耗)',
  `balance_after` int NOT NULL COMMENT '变动后余额快照',
  `source_type` tinyint NOT NULL COMMENT '来源: 1抽奖获得 2商城兑换',
  `related_id` bigint DEFAULT NULL COMMENT '关联业务行ID(可空)',
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT '业务幂等键',
  `remark` varchar(200) DEFAULT NULL COMMENT '人类可读描述',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_starlight_log_user_idempotency` (`user_id`,`idempotency_key`),
  KEY `idx_starlight_log_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='萌星辉流水（只增）';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `starlight_shop_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(80) NOT NULL COMMENT '商品名称',
  `category` varchar(20) NOT NULL COMMENT '分类: HOT/LIMITED/COSMETIC/UTILITY',
  `tag` varchar(20) DEFAULT NULL COMMENT '展示角标: 热门/限定/新品等',
  `price_starlight` int NOT NULL COMMENT '兑换所需萌星辉',
  `reward_type` varchar(30) NOT NULL COMMENT '奖励类型: VIP_DAYS/LOTTERY_VOUCHER',
  `reward_value` int NOT NULL COMMENT '奖励数值(如 VIP 天数)',
  `stock_remaining` int NOT NULL DEFAULT '-1' COMMENT '剩余库存,-1不限量',
  `daily_limit` int NOT NULL DEFAULT '0' COMMENT '每日限购次数,0不限',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '展示排序,越小越靠前',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1上架 0下架',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_starlight_shop_item_cat` (`category`,`enabled`,`delete_state`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='萌星辉商城商品';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `starlight_exchange_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `item_id` bigint NOT NULL COMMENT '商品ID',
  `item_name` varchar(80) NOT NULL COMMENT '商品名称快照',
  `price_paid` int NOT NULL COMMENT '实付萌星辉',
  `reward_type` varchar(30) NOT NULL COMMENT '发放类型快照',
  `reward_value` int NOT NULL COMMENT '发放数值快照',
  `idempotency_key` varchar(128) NOT NULL COMMENT '兑换幂等键',
  `use_status` tinyint NOT NULL DEFAULT '0' COMMENT '使用状态：0=未使用 1=已使用',
  `use_time` datetime DEFAULT NULL COMMENT '使用时间',
  `actual_grant_tier` tinyint DEFAULT NULL COMMENT '实际发放档位',
  `actual_duration_hours` int DEFAULT NULL COMMENT '实际延长小时数',
  `grant_summary` varchar(255) DEFAULT NULL COMMENT '发放结果摘要',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_starlight_exchange_user_idem` (`user_id`,`idempotency_key`),
  KEY `idx_starlight_exchange_user_time` (`user_id`,`create_time`),
  KEY `idx_starlight_exchange_user_item_day` (`user_id`,`item_id`,`create_time`),
  KEY `idx_starlight_exchange_user_use` (`user_id`,`use_status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='萌星辉兑换记录';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_vip_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `vip_tier` tinyint NOT NULL DEFAULT '0' COMMENT 'VIP档位: 0普通 1PRO 2MAX',
  `vip_expire_at` datetime DEFAULT NULL COMMENT 'VIP到期时间',
  `base_quota_tier` tinyint NOT NULL DEFAULT '0' COMMENT '基础配额档位',
  `quota_period_start` datetime DEFAULT NULL COMMENT '基础配额周期开始',
  `quota_period_end` datetime DEFAULT NULL COMMENT '基础配额周期结束',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除：0=正常 1=已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_vip_subscription_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户VIP订阅（economy 权威）';
CREATE TABLE `vip_purchase_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `vip_tier` tinyint NOT NULL COMMENT '购买档位: 1PRO 2MAX',
  `paid_amount` decimal(10,2) NOT NULL COMMENT '实付金额',
  `payment_order_no` varchar(96) NOT NULL COMMENT '支付订单号',
  `payment_state` tinyint NOT NULL DEFAULT '0' COMMENT '0待支付 1成功 2关闭',
  `period_start` datetime DEFAULT NULL COMMENT '订阅周期开始',
  `period_end` datetime DEFAULT NULL COMMENT '订阅周期结束',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vip_purchase_order_no` (`payment_order_no`),
  KEY `idx_vip_purchase_user_state` (`user_id`,`payment_state`,`delete_state`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会员支付成功与首购资格流水';
/*!40101 SET character_set_client = @saved_cs_client */;
-- ----------------------------
-- Demo seeds: checkin / lottery / vip quota
-- ----------------------------
INSERT INTO checkin_rule (month, day_number, points, is_surprise) VALUES
(0,1,31,0),(0,2,46,0),(0,3,42,0),(0,4,49,0),(0,5,32,0),(0,6,39,0),(0,7,41,0),
(0,8,34,0),(0,9,44,0),(0,10,38,1),(0,11,50,0),(0,12,46,0),(0,13,41,0),(0,14,34,0),
(0,15,45,0),(0,16,49,0),(0,17,49,0),(0,18,40,0),(0,19,50,0),(0,20,43,1),(0,21,36,0),
(0,22,37,0),(0,23,37,0),(0,24,42,0),(0,25,43,0),(0,26,44,0),(0,27,41,0),(0,28,47,0),
(0,29,32,0),(0,30,45,1),(0,31,32,0);

INSERT INTO `checkin_streak_reward`
(`streak_days`, `reward_type`, `bonus_points`, `starlight_amount`, `makeup_card_amount`, `vip_days`, `title`, `subtitle`, `description`)
VALUES
(3,  'STARLIGHT',   0, 100, 0, 0, '连续签到 3 天', '萌星辉 + 100', '连续签到3天萌星辉奖励'),
(7,  'MAKEUP_CARD', 0, 0, 3, 0, '连续签到 7 天', '补签卡 ×3', '连续签到7天补签卡奖励'),
(15, 'MIXED',       0, 300, 0, 1, '连续签到 15 天', '一日会员体验 + 300 萌星辉', '连续签到15天混合奖励'),
(30, 'MIXED',       0, 500, 0, 3, '连续签到 30 天', '三日会员体验 + 500 萌星辉', '连续签到30天混合奖励');

-- 惊喜奖池：类间权重 积分30/星辉25/补签卡20/抵扣券15/VIP10；类内低≈70/中≈20/高≈10（绝对权重合计 1000）
INSERT INTO `checkin_surprise_pool` (`reward_type`, `reward_value`, `weight`, `label`, `sort_order`) VALUES
('POINTS', 200, 70, '+200 积分', 10),
('POINTS', 250, 70, '+250 积分', 11),
('POINTS', 300, 70, '+300 积分', 12),
('POINTS', 350, 30, '+350 积分', 13),
('POINTS', 400, 30, '+400 积分', 14),
('POINTS', 450, 15, '+450 积分', 15),
('POINTS', 500, 15, '+500 积分', 16),
('STARLIGHT', 100, 60, '+100 萌星辉', 20),
('STARLIGHT', 150, 55, '+150 萌星辉', 21),
('STARLIGHT', 200, 60, '+200 萌星辉', 22),
('STARLIGHT', 250, 25, '+250 萌星辉', 23),
('STARLIGHT', 300, 25, '+300 萌星辉', 24),
('STARLIGHT', 400, 12, '+400 萌星辉', 25),
('STARLIGHT', 500, 13, '+500 萌星辉', 26),
('MAKEUP_CARD', 5, 35, '+5 张补签卡', 30),
('MAKEUP_CARD', 8, 35, '+8 张补签卡', 31),
('MAKEUP_CARD', 10, 35, '+10 张补签卡', 32),
('MAKEUP_CARD', 15, 35, '+15 张补签卡', 33),
('MAKEUP_CARD', 18, 40, '+18 张补签卡', 34),
('MAKEUP_CARD', 20, 20, '+20 张补签卡', 35),
('LOTTERY_VOUCHER', 10, 26, '+10 张抽奖抵扣券', 40),
('LOTTERY_VOUCHER', 20, 26, '+20 张抽奖抵扣券', 41),
('LOTTERY_VOUCHER', 30, 27, '+30 张抽奖抵扣券', 42),
('LOTTERY_VOUCHER', 40, 26, '+40 张抽奖抵扣券', 43),
('LOTTERY_VOUCHER', 50, 15, '+50 张抽奖抵扣券', 44),
('LOTTERY_VOUCHER', 60, 15, '+60 张抽奖抵扣券', 45),
('LOTTERY_VOUCHER', 80, 7, '+80 张抽奖抵扣券', 46),
('LOTTERY_VOUCHER', 100, 8, '+100 张抽奖抵扣券', 47),
('VIP_DAYS', 1, 23, 'PRO 体验 1 日', 50),
('VIP_DAYS', 2, 24, 'PRO 体验 2 日', 51),
('VIP_DAYS', 3, 23, 'PRO 体验 3 日', 52),
('VIP_DAYS', 4, 10, 'PRO 体验 4 日', 53),
('VIP_DAYS', 5, 10, 'PRO 体验 5 日', 54),
('VIP_DAYS', 6, 5, 'PRO 体验 6 日', 55),
('VIP_DAYS', 7, 5, 'PRO 体验 7 日', 56);

-- 抽奖演示数据（phase=1 进行中，供用户端 / 首页趋势）
-- ----------------------------
INSERT INTO `lottery_prize` (`id`, `name`, `prize_type`, `prize_value`, `stock_quantity`, `catalog_status`, `is_mystery_bundle`, `image_path`) VALUES
    (1, '谢谢参与', 0, 0, -1, 1, 0, NULL),
    (2, '神秘大奖', 1, 0, -1, 1, 1, NULL),
    (3, '周边小礼品A', 2, 0, -1, 1, 0, NULL),
    (4, '安慰奖', 3, 0, -1, 1, 0, NULL),
    (5, '10~50随机积分', 4, -1, -1, 1, 0, NULL),
    (6, '50积分', 4, 50, -1, 0, 0, NULL),
    (7, 'PRO会员体验卡·30天', 5, 30, -1, 1, 0, NULL),
    (8, 'VIP体验3天', 5, 3, -1, 2, 0, NULL);

-- 神秘大奖子奖项：会员奖项只保留一个 MAX 30 天，权重压到千分之一
INSERT INTO `lottery_prize_mystery_item` (`id`, `prize_id`, `item_type`, `item_value`, `vip_tier`, `weight`) VALUES
    (2, 2, 4, 100, NULL, 500),
    (3, 2, 4, 80, NULL, 500),
    (4, 2, 5, 30, 2, 1);

INSERT INTO `lottery_activity` (`id`, `title`, `description`, `cover_image_url`, `publisher_id`, `cost_points_per_draw`, `status`, `phase`, `start_time`, `end_time`, `delete_state`)
VALUES (1, '积分幸运抽',
        '单次消耗积分参与抽奖；积分奖即时到账，其它奖品以站内通知为准。概率按活动权重动态计算（售罄档位自动剔除并重算）。十连 Soft：至少 1 件稀有档（大奖/周边/VIP）；累计 50 抽未出神秘大奖则下一次必出神秘大奖档。',
        NULL, NULL, 30, 1, 1, NULL, NULL, 0);

INSERT INTO `lottery_activity_prize` (`id`, `activity_id`, `prize_id`, `weight`, `stock_remaining`, `is_jackpot`, `image_path`) VALUES
    (1, 1, 1, 5000, -1, 0, NULL),
    (2, 1, 2, 20, 1, 1, NULL),
    (3, 1, 3, 500, 200, 0, NULL),
    (4, 1, 4, 2000, -1, 0, NULL),
    (5, 1, 5, 2300, -1, 0, NULL),
    -- 体验卡由 1 天改 30 天，价值 30 倍，权重从 180 压到 20（约千分之二）并限量
    (7, 1, 7, 20, 30, 0, NULL);

INSERT INTO `lottery_pool_task` (`activity_id`, `task_code`, `title`, `target_count`, `voucher_reward`, `sort_order`, `enabled`) VALUES
(1, 'COMMENT_1', '评论 1 次', 1, 1, 10, 1),
(1, 'LIKE_3', '点赞 3 次', 3, 1, 20, 1),
(1, 'CHECKIN_TODAY', '今日签到', 1, 2, 30, 1);

INSERT INTO `starlight_shop_item`
(`name`, `category`, `tag`, `price_starlight`, `reward_type`, `reward_value`, `stock_remaining`, `daily_limit`, `sort_order`, `enabled`, `delete_state`) VALUES
('AI额度重置卡', 'HOT', '限定', 600, 'QUOTA_RESET', 1, -1, 1, 1, 1, 0),
('抽奖抵扣券·1张', 'HOT', '热门', 10, 'LOTTERY_VOUCHER', 1, -1, 0, 20, 1, 0),
('抽奖抵扣券·10张', 'HOT', NULL, 90, 'LOTTERY_VOUCHER', 10, -1, 0, 21, 1, 0),
('抽奖抵扣券·30张', 'HOT', '热门', 270, 'LOTTERY_VOUCHER', 30, -1, 0, 22, 1, 0),
('抽奖抵扣券·50张', 'HOT', '新品', 450, 'LOTTERY_VOUCHER', 50, 5000, 0, 23, 1, 0),
('抽奖抵扣券·100张', 'HOT', '限定', 900, 'LOTTERY_VOUCHER', 100, 2000, 0, 24, 1, 0),
('签到补签卡·1张', 'HOT', '实用', 50, 'MAKEUP_CARD', 1, -1, 0, 30, 1, 0),
('签到补签卡·10张', 'HOT', '热门', 450, 'MAKEUP_CARD', 10, 1000, 0, 31, 1, 0),
('签到补签卡·30张', 'HOT', '限定', 1400, 'MAKEUP_CARD', 30, 500, 0, 32, 1, 0);

INSERT INTO `lottery_collect_milestone`
(`threshold_count`, `reward_type`, `reward_value`, `alt_reward_value`, `label`, `sort_order`, `enabled`, `delete_state`) VALUES
(10, 'RANDOM', 1, 30, '抵扣券×1', 10, 1, 0),
(25, 'POINTS', 50, NULL, '积分×50', 20, 1, 0),
(50, 'VOUCHER', 3, NULL, '抵扣券×3', 30, 1, 0),
(80, 'VIP_DAYS', 1, NULL, 'VIP·1天', 40, 1, 0);

