-- economy domain full schema
DROP DATABASE IF EXISTS `forum_economy_db`;

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
  `points` int NOT NULL DEFAULT '0' COMMENT '签到获得积分',
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
  `bonus_points` int NOT NULL DEFAULT '0' COMMENT '额外奖励积分',
  `description` varchar(100) DEFAULT NULL COMMENT '奖励描述',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uix_streak_days` (`streak_days`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='连续签到奖励表';
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
  `name` varchar(100) NOT NULL COMMENT '表情包名称',
  `description` varchar(100) DEFAULT NULL COMMENT '表情包说明(上传者填写, 展示于详情, 最多100字)',
  `cover_url` varchar(500) NOT NULL COMMENT '封面预览图URL',
  `price` int NOT NULL DEFAULT '0' COMMENT '售价积分',
  `upload_user_id` bigint DEFAULT NULL COMMENT '上传者ID, NULL 表示站长推荐',
  `sales_count` int NOT NULL DEFAULT '0' COMMENT '销售数量',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0待审核 1上架 2下架',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_upload_user_id` (`upload_user_id`),
  KEY `idx_status_sales` (`status`,`sales_count`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='表情包商品表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_question` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'é¢˜ç›®ID',
  `bank_id` bigint NOT NULL COMMENT 'é¢˜åº“ID',
  `question_order` int NOT NULL COMMENT 'é¢˜ç›®é¡ºåº',
  `source_no` varchar(30) DEFAULT NULL COMMENT 'åŽŸæ–‡é¢˜å·',
  `section_name` varchar(100) DEFAULT NULL COMMENT 'åŽŸæ–‡ç« èŠ‚æˆ–åˆ†ç»„',
  `question_type` varchar(20) NOT NULL COMMENT 'é¢˜ç›®ç±»åž‹',
  `stem` text NOT NULL COMMENT 'é¢˜å¹²',
  `options_json` json DEFAULT NULL COMMENT 'é€‰é¡¹JSON',
  `standard_answer` text COMMENT 'æ ‡å‡†ç­”æ¡ˆ',
  `explanation` text COMMENT 'è§£æž',
  `answer_inferred_from_user` tinyint NOT NULL DEFAULT '0' COMMENT 'ç­”æ¡ˆæ˜¯å¦ä»Žç”¨æˆ·ç­”æ¡ˆæŽ¨æ–­',
  `needs_option_review` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦éœ€è¦äººå·¥å¤æ ¸é€‰é¡¹',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  KEY `idx_bank_order` (`bank_id`,`delete_state`,`question_order`)
) ENGINE=InnoDB AUTO_INCREMENT=1704 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è€ƒè¯•é¢˜åº“é¢˜ç›®è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_question_bank` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'é¢˜åº“ID',
  `user_id` bigint NOT NULL COMMENT 'åˆ›å»ºç”¨æˆ·ID',
  `subject` varchar(100) NOT NULL COMMENT 'è€ƒè¯•ç§‘ç›®',
  `source_name` varchar(255) NOT NULL COMMENT 'æ¥æºæ–‡ä»¶å',
  `total_count` int NOT NULL DEFAULT '0' COMMENT 'æ€»é¢˜æ•°',
  `choice_count` int NOT NULL DEFAULT '0' COMMENT 'é€‰æ‹©é¢˜æ•°é‡',
  `judgement_count` int NOT NULL DEFAULT '0' COMMENT 'åˆ¤æ–­é¢˜æ•°é‡',
  `subjective_count` int NOT NULL DEFAULT '0' COMMENT 'ä¸»è§‚é¢˜æ•°é‡',
  `warnings_json` json DEFAULT NULL COMMENT 'è§£æžè­¦å‘ŠJSON',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  KEY `idx_user_subject_time` (`user_id`,`subject`,`delete_state`,`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=17 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è€ƒè¯•é¢˜åº“ä¸»è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `exam_question_user_progress` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'è¿›åº¦ID',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `bank_id` bigint NOT NULL COMMENT 'é¢˜åº“ID',
  `question_id` bigint NOT NULL COMMENT 'é¢˜ç›®ID',
  `answer_text` varchar(1000) DEFAULT NULL COMMENT 'ç”¨æˆ·ç­”æ¡ˆ',
  `answered` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦å·²ä½œç­”: 0å¦ 1æ˜¯',
  `correct` tinyint DEFAULT NULL COMMENT 'æ˜¯å¦ç­”å¯¹: 0å¦ 1æ˜¯',
  `wrong` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦é”™é¢˜: 0å¦ 1æ˜¯',
  `focus` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦é‡ç‚¹è®°å¿†: 0å¦ 1æ˜¯',
  `judge_score` int DEFAULT NULL COMMENT 'ä¸»è§‚é¢˜è¯„åˆ†',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤: 0å¦ 1æ˜¯',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_exam_progress_user_question` (`user_id`,`question_id`),
  KEY `idx_exam_progress_user_bank` (`user_id`,`bank_id`,`delete_state`),
  KEY `idx_exam_progress_user_focus` (`user_id`,`focus`,`delete_state`),
  KEY `idx_exam_progress_user_wrong` (`user_id`,`wrong`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='è€ƒè¯•é¢˜åº“ç”¨æˆ·ç­”é¢˜è¿›åº¦è¡¨';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `forum_vip_quota_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `vip_tier` tinyint NOT NULL COMMENT '1=PRO 2=MAX',
  `quota_key` varchar(64) NOT NULL COMMENT '配置键',
  `group_label` varchar(64) NOT NULL COMMENT '分组标题',
  `display_name` varchar(100) NOT NULL COMMENT '展示名称',
  `quota_type` varchar(32) NOT NULL COMMENT 'unlimited|daily_count|token_period',
  `daily_bucket` varchar(32) DEFAULT NULL COMMENT '日配额桶',
  `model_code` varchar(64) DEFAULT NULL COMMENT 'token_period 时按模型汇总 forum_ai_usage_log',
  `icon_provider` varchar(32) DEFAULT NULL COMMENT 'deepseek|qwen|gemini|claude|openai|huanapi',
  `daily_limit` int DEFAULT NULL COMMENT '日次数上限',
  `token_limit` bigint DEFAULT NULL COMMENT '周期 Token 上限',
  `tier_tag` varchar(16) DEFAULT NULL COMMENT 'PRO|MAX|免费 角标',
  `sort_order` int NOT NULL DEFAULT '0' COMMENT '排序',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '1启用',
  PRIMARY KEY (`id`),
  KEY `idx_vip_quota_tier_sort` (`vip_tier`,`sort_order`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='VIP配额展示配置';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `growth_challenge` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鎸戞垬ID',
  `challenge_code` varchar(40) NOT NULL COMMENT '鎸戞垬缂栫爜',
  `challenge_type` varchar(30) NOT NULL COMMENT '鎸戞垬绫诲瀷',
  `title` varchar(80) NOT NULL COMMENT '鎸戞垬鏍囬?',
  `description` varchar(500) DEFAULT NULL COMMENT '鎸戞垬璇存槑',
  `bank_id` bigint NOT NULL COMMENT '鍏宠仈棰樺簱ID',
  `question_count` int NOT NULL DEFAULT '10' COMMENT '鎶介?鏁',
  `passing_score` int NOT NULL DEFAULT '80' COMMENT '鍙婃牸鍒',
  `max_attempts_per_day` int NOT NULL DEFAULT '3' COMMENT '姣忔棩鏈?ぇ灏濊瘯鏁',
  `experience_reward` int NOT NULL DEFAULT '0' COMMENT '閫氳繃缁忛獙濂栧姳',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '鏄?惁鍚?敤: 0鍚?1鏄',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0鍚?1鏄',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_growth_challenge_code` (`challenge_code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎴愰暱鎸戞垬瀹氫箟';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `growth_challenge_attempt` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鎸戞垬灏濊瘯ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `challenge_id` bigint NOT NULL COMMENT '鎸戞垬ID',
  `attempt_no` int NOT NULL COMMENT '灏濊瘯搴忓彿',
  `status` varchar(20) NOT NULL COMMENT '灏濊瘯鐘舵?',
  `question_ids_json` json NOT NULL COMMENT '鏈??棰樼洰ID',
  `answers_json` json DEFAULT NULL COMMENT '鐢ㄦ埛绛旀?',
  `score` int DEFAULT NULL COMMENT '寰楀垎',
  `started_at` datetime NOT NULL COMMENT '寮??鏃堕棿',
  `submitted_at` datetime DEFAULT NULL COMMENT '鎻愪氦鏃堕棿',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0鍚?1鏄',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_growth_attempt_user_challenge_no` (`user_id`,`challenge_id`,`attempt_no`),
  KEY `idx_growth_attempt_user_challenge` (`user_id`,`challenge_id`,`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎴愰暱鎸戞垬灏濊瘯璁板綍';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `growth_experience_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '缁忛獙娴佹按ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `source_type` varchar(30) NOT NULL COMMENT '缁忛獙鏉ユ簮绫诲瀷',
  `source_business_id` bigint NOT NULL COMMENT '鏉ユ簮涓氬姟ID',
  `experience_delta` int NOT NULL COMMENT '缁忛獙鍙樺姩',
  `remark` varchar(200) DEFAULT NULL COMMENT '澶囨敞',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0鍚?1鏄',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_growth_experience_source` (`user_id`,`source_type`,`source_business_id`)
) ENGINE=InnoDB AUTO_INCREMENT=19 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎴愰暱缁忛獙娴佹按';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `growth_reward_record` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '濂栧姳娴佹按ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `challenge_id` bigint NOT NULL COMMENT '鎸戞垬ID',
  `reward_type` varchar(30) NOT NULL COMMENT '濂栧姳绫诲瀷',
  `reward_value` varchar(100) DEFAULT NULL COMMENT '濂栧姳鍊',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0鍚?1鏄',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_growth_reward_user_challenge_type` (`user_id`,`challenge_id`,`reward_type`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鎴愰暱濂栧姳娴佹按';
/*!40101 SET character_set_client = @saved_cs_client */;
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
  `draw_batch_key` varchar(40) DEFAULT NULL COMMENT '十连批次UUID',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '是否删除: 0否 1是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_activity_id` (`activity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=142 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='抽奖记录表';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `lottery_draw_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `activity_id` bigint NOT NULL COMMENT 'æ´»åŠ¨ID',
  `request_id` varchar(64) NOT NULL COMMENT 'å®¢æˆ·ç«¯å¹‚ç­‰é”®',
  `times` int NOT NULL COMMENT 'æŠ½å¥–æ¬¡æ•° 1æˆ–10',
  `batch_key` varchar(40) DEFAULT NULL COMMENT 'æ‰¹æ¬¡é”®ï¼Œå…³è” lottery_draw_record.draw_batch_key',
  `pity_after` int DEFAULT NULL COMMENT 'æ‰¹æ¬¡ç»“æŸåŽçš„ç¡¬ä¿åº•è®¡æ•°',
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT 'æ˜¯å¦åˆ é™¤: 0å¦ 1æ˜¯',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_lottery_user_request` (`user_id`,`request_id`),
  KEY `idx_lottery_request_batch` (`batch_key`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='æŠ½å¥–è¯·æ±‚å¹‚ç­‰è¡¨';
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
  `idempotency_key` varchar(128) DEFAULT NULL COMMENT 'ä¸šåŠ¡å¹‚ç­‰é”®ï¼Œä¸€æ¬¡æ€§å˜åŠ¨å¿…å¡«',
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
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·IDï¼ˆé€»è¾‘å…³è” auth.userï¼Œæ— è·¨åº“å¤–é”®ï¼‰',
  `balance` int NOT NULL DEFAULT '0' COMMENT 'å½“å‰ç§¯åˆ†ä½™é¢',
  `version` int NOT NULL DEFAULT '0' COMMENT 'ä¹è§‚é”ç‰ˆæœ¬å·',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤ï¼š0=æ­£å¸¸ 1=å·²åˆ é™¤',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_points_wallet_user_id` (`user_id`),
  KEY `idx_points_wallet_delete_state` (`delete_state`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ç§¯åˆ†é’±åŒ…ï¼ˆeconomy æƒå¨ï¼‰';
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
CREATE TABLE `user_growth_profile` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '鎴愰暱妗ｆ?ID',
  `user_id` bigint NOT NULL COMMENT '鐢ㄦ埛ID',
  `formal_state` tinyint NOT NULL DEFAULT '0' COMMENT '姝ｅ紡鐢ㄦ埛鐘舵?: 0闈炴?寮?1姝ｅ紡',
  `experience` int NOT NULL DEFAULT '0' COMMENT '鎴愰暱缁忛獙',
  `growth_level` int NOT NULL DEFAULT '1' COMMENT '鎴愰暱绛夌骇',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '閫昏緫鍒犻櫎: 0鍚?1鏄',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_growth_profile_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=34 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='鐢ㄦ埛鎴愰暱妗ｆ?';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_lottery_pity` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `pity_draws` int NOT NULL DEFAULT '0' COMMENT 'è·ä¸Šæ¬¡ç¥žç§˜å¤§å¥–å·²è¿žç»­å¼€å¥–æ¬¡æ•°',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤ï¼š0=æ­£å¸¸ 1=å·²åˆ é™¤',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_lottery_pity_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='æŠ½å¥–ç¡¬ä¿åº•è®¡æ•°ï¼ˆeconomy æƒå¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_vip_subscription` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'ä¸»é”®',
  `user_id` bigint NOT NULL COMMENT 'ç”¨æˆ·ID',
  `vip_tier` tinyint NOT NULL DEFAULT '0' COMMENT 'VIPæ¡£ä½: 0æ™®é€š 1PRO 2MAX',
  `vip_expire_at` datetime DEFAULT NULL COMMENT 'VIPåˆ°æœŸæ—¶é—´',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'åˆ›å»ºæ—¶é—´',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'æ›´æ–°æ—¶é—´',
  `delete_state` tinyint(1) NOT NULL DEFAULT '0' COMMENT 'é€»è¾‘åˆ é™¤ï¼š0=æ­£å¸¸ 1=å·²åˆ é™¤',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_vip_subscription_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=29 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='ç”¨æˆ·VIPè®¢é˜…ï¼ˆeconomy æƒå¨ï¼‰';
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `vip_trial_entitlement` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '体验会员ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `trial_code` varchar(30) NOT NULL COMMENT '体验编码',
  `status` varchar(20) NOT NULL COMMENT '状态: ACTIVE EXPIRED SUPERSEDED',
  `expire_at` datetime NOT NULL COMMENT '体验到期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `delete_state` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0否 1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_vip_trial_user_code` (`user_id`,`trial_code`),
  KEY `idx_vip_trial_active` (`user_id`,`status`,`expire_at`,`delete_state`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='体验会员权益';
/*!40101 SET character_set_client = @saved_cs_client */;
