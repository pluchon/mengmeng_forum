-- 从 forum_db 结构+数据复制到各服务库（可重复执行：先 DROP 目标表再 LIKE+INSERT）
SET NAMES utf8mb4;

-- ========== economy ==========
USE forum_economy_db;
DROP TABLE IF EXISTS points_wallet, points_log, checkin_log, checkin_rule, checkin_streak_reward, user_checkin_info,
  lottery_activity, lottery_activity_prize, lottery_draw_hourly_stat, lottery_draw_record, lottery_draw_request,
  lottery_prize, lottery_prize_mystery_item, user_lottery_pity, emoji_shop, emoji_item, user_emoji,
  growth_challenge, growth_challenge_attempt, growth_experience_log, growth_reward_record, user_growth_profile,
  exam_question, exam_question_bank, exam_question_user_progress, user_vip_subscription, vip_trial_entitlement, forum_vip_quota_config;
CREATE TABLE points_wallet LIKE forum_db.points_wallet; INSERT INTO points_wallet SELECT * FROM forum_db.points_wallet;
CREATE TABLE points_log LIKE forum_db.points_log; INSERT INTO points_log SELECT * FROM forum_db.points_log;
CREATE TABLE checkin_log LIKE forum_db.checkin_log; INSERT INTO checkin_log SELECT * FROM forum_db.checkin_log;
CREATE TABLE checkin_rule LIKE forum_db.checkin_rule; INSERT INTO checkin_rule SELECT * FROM forum_db.checkin_rule;
CREATE TABLE checkin_streak_reward LIKE forum_db.checkin_streak_reward; INSERT INTO checkin_streak_reward SELECT * FROM forum_db.checkin_streak_reward;
CREATE TABLE user_checkin_info LIKE forum_db.user_checkin_info; INSERT INTO user_checkin_info SELECT * FROM forum_db.user_checkin_info;
CREATE TABLE lottery_activity LIKE forum_db.lottery_activity; INSERT INTO lottery_activity SELECT * FROM forum_db.lottery_activity;
CREATE TABLE lottery_activity_prize LIKE forum_db.lottery_activity_prize; INSERT INTO lottery_activity_prize SELECT * FROM forum_db.lottery_activity_prize;
CREATE TABLE lottery_draw_hourly_stat LIKE forum_db.lottery_draw_hourly_stat; INSERT INTO lottery_draw_hourly_stat SELECT * FROM forum_db.lottery_draw_hourly_stat;
CREATE TABLE lottery_draw_record LIKE forum_db.lottery_draw_record; INSERT INTO lottery_draw_record SELECT * FROM forum_db.lottery_draw_record;
CREATE TABLE lottery_draw_request LIKE forum_db.lottery_draw_request; INSERT INTO lottery_draw_request SELECT * FROM forum_db.lottery_draw_request;
CREATE TABLE lottery_prize LIKE forum_db.lottery_prize; INSERT INTO lottery_prize SELECT * FROM forum_db.lottery_prize;
CREATE TABLE lottery_prize_mystery_item LIKE forum_db.lottery_prize_mystery_item; INSERT INTO lottery_prize_mystery_item SELECT * FROM forum_db.lottery_prize_mystery_item;
CREATE TABLE user_lottery_pity LIKE forum_db.user_lottery_pity; INSERT INTO user_lottery_pity SELECT * FROM forum_db.user_lottery_pity;
CREATE TABLE emoji_shop LIKE forum_db.emoji_shop; INSERT INTO emoji_shop SELECT * FROM forum_db.emoji_shop;
CREATE TABLE emoji_item LIKE forum_db.emoji_item; INSERT INTO emoji_item SELECT * FROM forum_db.emoji_item;
CREATE TABLE user_emoji LIKE forum_db.user_emoji; INSERT INTO user_emoji SELECT * FROM forum_db.user_emoji;
CREATE TABLE growth_challenge LIKE forum_db.growth_challenge; INSERT INTO growth_challenge SELECT * FROM forum_db.growth_challenge;
CREATE TABLE growth_challenge_attempt LIKE forum_db.growth_challenge_attempt; INSERT INTO growth_challenge_attempt SELECT * FROM forum_db.growth_challenge_attempt;
CREATE TABLE growth_experience_log LIKE forum_db.growth_experience_log; INSERT INTO growth_experience_log SELECT * FROM forum_db.growth_experience_log;
CREATE TABLE growth_reward_record LIKE forum_db.growth_reward_record; INSERT INTO growth_reward_record SELECT * FROM forum_db.growth_reward_record;
CREATE TABLE user_growth_profile LIKE forum_db.user_growth_profile; INSERT INTO user_growth_profile SELECT * FROM forum_db.user_growth_profile;
CREATE TABLE exam_question LIKE forum_db.exam_question; INSERT INTO exam_question SELECT * FROM forum_db.exam_question;
CREATE TABLE exam_question_bank LIKE forum_db.exam_question_bank; INSERT INTO exam_question_bank SELECT * FROM forum_db.exam_question_bank;
CREATE TABLE exam_question_user_progress LIKE forum_db.exam_question_user_progress; INSERT INTO exam_question_user_progress SELECT * FROM forum_db.exam_question_user_progress;
CREATE TABLE user_vip_subscription LIKE forum_db.user_vip_subscription; INSERT INTO user_vip_subscription SELECT * FROM forum_db.user_vip_subscription;
CREATE TABLE vip_trial_entitlement LIKE forum_db.vip_trial_entitlement; INSERT INTO vip_trial_entitlement SELECT * FROM forum_db.vip_trial_entitlement;
CREATE TABLE forum_vip_quota_config LIKE forum_db.forum_vip_quota_config; INSERT INTO forum_vip_quota_config SELECT * FROM forum_db.forum_vip_quota_config;

-- ========== auth ==========
USE forum_auth_db;
DROP TABLE IF EXISTS user, user_login_log, user_follow, sys_dept, sys_dict_data, sys_dict_type, sys_menu, sys_role, sys_role_menu, sys_user_role;
CREATE TABLE user LIKE forum_db.user; INSERT INTO user SELECT * FROM forum_db.user;
CREATE TABLE user_login_log LIKE forum_db.user_login_log; INSERT INTO user_login_log SELECT * FROM forum_db.user_login_log;
CREATE TABLE user_follow LIKE forum_db.user_follow; INSERT INTO user_follow SELECT * FROM forum_db.user_follow;
CREATE TABLE sys_dept LIKE forum_db.sys_dept; INSERT INTO sys_dept SELECT * FROM forum_db.sys_dept;
CREATE TABLE sys_dict_data LIKE forum_db.sys_dict_data; INSERT INTO sys_dict_data SELECT * FROM forum_db.sys_dict_data;
CREATE TABLE sys_dict_type LIKE forum_db.sys_dict_type; INSERT INTO sys_dict_type SELECT * FROM forum_db.sys_dict_type;
CREATE TABLE sys_menu LIKE forum_db.sys_menu; INSERT INTO sys_menu SELECT * FROM forum_db.sys_menu;
CREATE TABLE sys_role LIKE forum_db.sys_role; INSERT INTO sys_role SELECT * FROM forum_db.sys_role;
CREATE TABLE sys_role_menu LIKE forum_db.sys_role_menu; INSERT INTO sys_role_menu SELECT * FROM forum_db.sys_role_menu;
CREATE TABLE sys_user_role LIKE forum_db.sys_user_role; INSERT INTO sys_user_role SELECT * FROM forum_db.sys_user_role;

-- ========== content ==========
USE forum_content_db;
DROP TABLE IF EXISTS article, article_favorite, article_image, article_like, article_reply, article_reply_like,
  article_reply_media, article_sub_reply, article_sub_reply_like, article_video_danmaku, board, category,
  forum_article_ai_feature, forum_article_tag, forum_article_tag_link, forum_article_tag_request,
  forum_user_ai_profile_snapshot, user_favorite_folder, user_interest_preference, user_recommend_feedback;
CREATE TABLE article LIKE forum_db.article; INSERT INTO article SELECT * FROM forum_db.article;
CREATE TABLE article_favorite LIKE forum_db.article_favorite; INSERT INTO article_favorite SELECT * FROM forum_db.article_favorite;
CREATE TABLE article_image LIKE forum_db.article_image; INSERT INTO article_image SELECT * FROM forum_db.article_image;
CREATE TABLE article_like LIKE forum_db.article_like; INSERT INTO article_like SELECT * FROM forum_db.article_like;
CREATE TABLE article_reply LIKE forum_db.article_reply; INSERT INTO article_reply SELECT * FROM forum_db.article_reply;
CREATE TABLE article_reply_like LIKE forum_db.article_reply_like; INSERT INTO article_reply_like SELECT * FROM forum_db.article_reply_like;
CREATE TABLE article_reply_media LIKE forum_db.article_reply_media; INSERT INTO article_reply_media SELECT * FROM forum_db.article_reply_media;
CREATE TABLE article_sub_reply LIKE forum_db.article_sub_reply; INSERT INTO article_sub_reply SELECT * FROM forum_db.article_sub_reply;
CREATE TABLE article_sub_reply_like LIKE forum_db.article_sub_reply_like; INSERT INTO article_sub_reply_like SELECT * FROM forum_db.article_sub_reply_like;
CREATE TABLE article_video_danmaku LIKE forum_db.article_video_danmaku; INSERT INTO article_video_danmaku SELECT * FROM forum_db.article_video_danmaku;
CREATE TABLE board LIKE forum_db.board; INSERT INTO board SELECT * FROM forum_db.board;
CREATE TABLE category LIKE forum_db.category; INSERT INTO category SELECT * FROM forum_db.category;
CREATE TABLE forum_article_ai_feature LIKE forum_db.forum_article_ai_feature; INSERT INTO forum_article_ai_feature SELECT * FROM forum_db.forum_article_ai_feature;
CREATE TABLE forum_article_tag LIKE forum_db.forum_article_tag; INSERT INTO forum_article_tag SELECT * FROM forum_db.forum_article_tag;
CREATE TABLE forum_article_tag_link LIKE forum_db.forum_article_tag_link; INSERT INTO forum_article_tag_link SELECT * FROM forum_db.forum_article_tag_link;
CREATE TABLE forum_article_tag_request LIKE forum_db.forum_article_tag_request; INSERT INTO forum_article_tag_request SELECT * FROM forum_db.forum_article_tag_request;
CREATE TABLE forum_user_ai_profile_snapshot LIKE forum_db.forum_user_ai_profile_snapshot; INSERT INTO forum_user_ai_profile_snapshot SELECT * FROM forum_db.forum_user_ai_profile_snapshot;
CREATE TABLE user_favorite_folder LIKE forum_db.user_favorite_folder; INSERT INTO user_favorite_folder SELECT * FROM forum_db.user_favorite_folder;
CREATE TABLE user_interest_preference LIKE forum_db.user_interest_preference; INSERT INTO user_interest_preference SELECT * FROM forum_db.user_interest_preference;
CREATE TABLE user_recommend_feedback LIKE forum_db.user_recommend_feedback; INSERT INTO user_recommend_feedback SELECT * FROM forum_db.user_recommend_feedback;

-- ========== im ==========
USE forum_im_db;
DROP TABLE IF EXISTS message, system_message, forum_notice, forum_outbox_message, group_chat, group_chat_join_request,
  group_chat_member, group_chat_message, group_chat_report, user_chat_emoji;
CREATE TABLE message LIKE forum_db.message; INSERT INTO message SELECT * FROM forum_db.message;
CREATE TABLE system_message LIKE forum_db.system_message; INSERT INTO system_message SELECT * FROM forum_db.system_message;
CREATE TABLE forum_notice LIKE forum_db.forum_notice; INSERT INTO forum_notice SELECT * FROM forum_db.forum_notice;
CREATE TABLE forum_outbox_message LIKE forum_db.forum_outbox_message; INSERT INTO forum_outbox_message SELECT * FROM forum_db.forum_outbox_message;
CREATE TABLE group_chat LIKE forum_db.group_chat; INSERT INTO group_chat SELECT * FROM forum_db.group_chat;
CREATE TABLE group_chat_join_request LIKE forum_db.group_chat_join_request; INSERT INTO group_chat_join_request SELECT * FROM forum_db.group_chat_join_request;
CREATE TABLE group_chat_member LIKE forum_db.group_chat_member; INSERT INTO group_chat_member SELECT * FROM forum_db.group_chat_member;
CREATE TABLE group_chat_message LIKE forum_db.group_chat_message; INSERT INTO group_chat_message SELECT * FROM forum_db.group_chat_message;
CREATE TABLE group_chat_report LIKE forum_db.group_chat_report; INSERT INTO group_chat_report SELECT * FROM forum_db.group_chat_report;
CREATE TABLE user_chat_emoji LIKE forum_db.user_chat_emoji; INSERT INTO user_chat_emoji SELECT * FROM forum_db.user_chat_emoji;

-- ========== game ==========
USE forum_game_db;
DROP TABLE IF EXISTS game_definition, game_gobang_match_record, game_gobang_room_move, game_jinzi_match_record, game_jinzi_room_move,
  game_match_record, game_room_move, game_room_player, game_settlement_event, game_tetris_pk_match_record, game_tetris_record, game_user_profile;
CREATE TABLE game_definition LIKE forum_db.game_definition; INSERT INTO game_definition SELECT * FROM forum_db.game_definition;
CREATE TABLE game_gobang_match_record LIKE forum_db.game_gobang_match_record; INSERT INTO game_gobang_match_record SELECT * FROM forum_db.game_gobang_match_record;
CREATE TABLE game_gobang_room_move LIKE forum_db.game_gobang_room_move; INSERT INTO game_gobang_room_move SELECT * FROM forum_db.game_gobang_room_move;
CREATE TABLE game_jinzi_match_record LIKE forum_db.game_jinzi_match_record; INSERT INTO game_jinzi_match_record SELECT * FROM forum_db.game_jinzi_match_record;
CREATE TABLE game_jinzi_room_move LIKE forum_db.game_jinzi_room_move; INSERT INTO game_jinzi_room_move SELECT * FROM forum_db.game_jinzi_room_move;
CREATE TABLE game_match_record LIKE forum_db.game_match_record; INSERT INTO game_match_record SELECT * FROM forum_db.game_match_record;
CREATE TABLE game_room_move LIKE forum_db.game_room_move; INSERT INTO game_room_move SELECT * FROM forum_db.game_room_move;
CREATE TABLE game_room_player LIKE forum_db.game_room_player; INSERT INTO game_room_player SELECT * FROM forum_db.game_room_player;
CREATE TABLE game_settlement_event LIKE forum_db.game_settlement_event; INSERT INTO game_settlement_event SELECT * FROM forum_db.game_settlement_event;
CREATE TABLE game_tetris_pk_match_record LIKE forum_db.game_tetris_pk_match_record; INSERT INTO game_tetris_pk_match_record SELECT * FROM forum_db.game_tetris_pk_match_record;
CREATE TABLE game_tetris_record LIKE forum_db.game_tetris_record; INSERT INTO game_tetris_record SELECT * FROM forum_db.game_tetris_record;
CREATE TABLE game_user_profile LIKE forum_db.game_user_profile; INSERT INTO game_user_profile SELECT * FROM forum_db.game_user_profile;

-- ========== ai ==========
USE forum_ai_db;
DROP TABLE IF EXISTS ai_usage_daily, forum_ai_call_record, forum_ai_creation_version, forum_ai_creation_workspace,
  forum_ai_long_term_memory, forum_ai_model_price, forum_ai_model_usage_daily, forum_ai_task_session, forum_ai_usage_log,
  forum_companion_message, forum_companion_session, forum_mascot_model, forum_mascot_related_recommendation,
  forum_mascot_related_recommendation_item, user_mascot_preference, drift_bottle, drift_bottle_comment, drift_bottle_pick_log, drift_bottle_report;
CREATE TABLE ai_usage_daily LIKE forum_db.ai_usage_daily; INSERT INTO ai_usage_daily SELECT * FROM forum_db.ai_usage_daily;
CREATE TABLE forum_ai_call_record LIKE forum_db.forum_ai_call_record; INSERT INTO forum_ai_call_record SELECT * FROM forum_db.forum_ai_call_record;
CREATE TABLE forum_ai_creation_version LIKE forum_db.forum_ai_creation_version; INSERT INTO forum_ai_creation_version SELECT * FROM forum_db.forum_ai_creation_version;
CREATE TABLE forum_ai_creation_workspace LIKE forum_db.forum_ai_creation_workspace; INSERT INTO forum_ai_creation_workspace SELECT * FROM forum_db.forum_ai_creation_workspace;
CREATE TABLE forum_ai_long_term_memory LIKE forum_db.forum_ai_long_term_memory; INSERT INTO forum_ai_long_term_memory SELECT * FROM forum_db.forum_ai_long_term_memory;
CREATE TABLE forum_ai_model_price LIKE forum_db.forum_ai_model_price; INSERT INTO forum_ai_model_price SELECT * FROM forum_db.forum_ai_model_price;
CREATE TABLE forum_ai_model_usage_daily LIKE forum_db.forum_ai_model_usage_daily; INSERT INTO forum_ai_model_usage_daily SELECT * FROM forum_db.forum_ai_model_usage_daily;
CREATE TABLE forum_ai_task_session LIKE forum_db.forum_ai_task_session; INSERT INTO forum_ai_task_session SELECT * FROM forum_db.forum_ai_task_session;
CREATE TABLE forum_ai_usage_log LIKE forum_db.forum_ai_usage_log; INSERT INTO forum_ai_usage_log SELECT * FROM forum_db.forum_ai_usage_log;
CREATE TABLE forum_companion_message LIKE forum_db.forum_companion_message; INSERT INTO forum_companion_message SELECT * FROM forum_db.forum_companion_message;
CREATE TABLE forum_companion_session LIKE forum_db.forum_companion_session; INSERT INTO forum_companion_session SELECT * FROM forum_db.forum_companion_session;
CREATE TABLE forum_mascot_model LIKE forum_db.forum_mascot_model; INSERT INTO forum_mascot_model SELECT * FROM forum_db.forum_mascot_model;
CREATE TABLE forum_mascot_related_recommendation LIKE forum_db.forum_mascot_related_recommendation; INSERT INTO forum_mascot_related_recommendation SELECT * FROM forum_db.forum_mascot_related_recommendation;
CREATE TABLE forum_mascot_related_recommendation_item LIKE forum_db.forum_mascot_related_recommendation_item; INSERT INTO forum_mascot_related_recommendation_item SELECT * FROM forum_db.forum_mascot_related_recommendation_item;
CREATE TABLE user_mascot_preference LIKE forum_db.user_mascot_preference; INSERT INTO user_mascot_preference SELECT * FROM forum_db.user_mascot_preference;
CREATE TABLE drift_bottle LIKE forum_db.drift_bottle; INSERT INTO drift_bottle SELECT * FROM forum_db.drift_bottle;
CREATE TABLE drift_bottle_comment LIKE forum_db.drift_bottle_comment; INSERT INTO drift_bottle_comment SELECT * FROM forum_db.drift_bottle_comment;
CREATE TABLE drift_bottle_pick_log LIKE forum_db.drift_bottle_pick_log; INSERT INTO drift_bottle_pick_log SELECT * FROM forum_db.drift_bottle_pick_log;
CREATE TABLE drift_bottle_report LIKE forum_db.drift_bottle_report; INSERT INTO drift_bottle_report SELECT * FROM forum_db.drift_bottle_report;
