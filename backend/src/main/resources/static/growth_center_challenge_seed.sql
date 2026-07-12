-- 成长中心 P0 官方题库与挑战配置。可重复执行，不覆盖运营后续调整的题目。

INSERT INTO exam_question_bank (user_id, subject, source_name, total_count, choice_count, judgement_count, subjective_count, warnings_json, delete_state)
SELECT 0, '成长中心·新人试炼', 'growth-center-formal-user', 10, 10, 0, 0, JSON_ARRAY(), 0
WHERE NOT EXISTS (SELECT 1 FROM exam_question_bank WHERE user_id = 0 AND subject = '成长中心·新人试炼' AND delete_state = 0);
INSERT INTO exam_question_bank (user_id, subject, source_name, total_count, choice_count, judgement_count, subjective_count, warnings_json, delete_state)
SELECT 0, '成长中心·会员体验', 'growth-center-vip-trial-900', 10, 10, 0, 0, JSON_ARRAY(), 0
WHERE NOT EXISTS (SELECT 1 FROM exam_question_bank WHERE user_id = 0 AND subject = '成长中心·会员体验' AND delete_state = 0);

SET @formal_bank := (SELECT id FROM exam_question_bank WHERE user_id = 0 AND subject = '成长中心·新人试炼' AND delete_state = 0 ORDER BY id DESC LIMIT 1);
SET @trial_bank := (SELECT id FROM exam_question_bank WHERE user_id = 0 AND subject = '成长中心·会员体验' AND delete_state = 0 ORDER BY id DESC LIMIT 1);

INSERT INTO exam_question (bank_id, question_order, source_no, section_name, question_type, stem, options_json, standard_answer, explanation, answer_inferred_from_user, needs_option_review, delete_state)
SELECT @formal_bank, source_no, source_no, '社区规则', 'SINGLE', stem, JSON_ARRAY(JSON_OBJECT('label','A','text',a),JSON_OBJECT('label','B','text',b)), 'A', explanation, 0, 0, 0
FROM (
    SELECT 1 source_no, '发现违规内容时，正确的做法是？' stem, '使用举报入口并提供真实说明' a, '在评论区攻击对方' b, '维护社区安全' explanation UNION ALL
    SELECT 2, '发布他人隐私信息是否允许？', '不允许，应尊重他人隐私', '允许，只要内容有热度', '保护隐私是基本规则' UNION ALL
    SELECT 3, '遇到陌生链接时应当？', '谨慎核实来源，不随意输入账号信息', '立刻点击并转发', '防范账号风险' UNION ALL
    SELECT 4, '评论交流应遵循什么原则？', '友善、就事论事', '人身攻击更有说服力', '文明交流' UNION ALL
    SELECT 5, '转载社区内容前应当？', '确认授权并注明来源', '直接复制即可', '尊重原创' UNION ALL
    SELECT 6, '账号密码应当？', '妥善保管且不与他人共用', '告诉陌生网友', '账号安全' UNION ALL
    SELECT 7, '发现账号异常登录时应当？', '及时修改密码并查看登录记录', '继续忽略', '及时处置风险' UNION ALL
    SELECT 8, '发布内容时应当？', '遵守法律和社区规则', '为了流量可以造谣', '内容责任' UNION ALL
    SELECT 9, '与他人意见不同可以？', '理性表达观点', '恶意骚扰对方', '尊重不同意见' UNION ALL
    SELECT 10, '社区功能遇到问题时可以？', '查看帮助或反馈问题', '发布无关攻击内容', '合理反馈'
) q
WHERE NOT EXISTS (SELECT 1 FROM exam_question WHERE bank_id=@formal_bank AND question_order=q.source_no AND delete_state=0);

INSERT INTO exam_question (bank_id, question_order, source_no, section_name, question_type, stem, options_json, standard_answer, explanation, answer_inferred_from_user, needs_option_review, delete_state)
SELECT @trial_bank, source_no, source_no, '会员体验', 'SINGLE', stem, JSON_ARRAY(JSON_OBJECT('label','A','text',a),JSON_OBJECT('label','B','text',b)), 'A', explanation, 0, 0, 0
FROM (
    SELECT 1 source_no, '会员体验有效期是？' stem, '7 天' a, '永久有效' b, '体验权益有明确期限' explanation UNION ALL
    SELECT 2, '体验会员可以领取几次？', '每个账号一次', '每天一次', '避免重复领取' UNION ALL
    SELECT 3, '体验会员的模型额度？', '使用独立体验额度', '等同完整付费额度', '体验额度独立管理' UNION ALL
    SELECT 4, '已有有效付费会员能否领取体验？', '不能', '可以叠加', '避免覆盖付费权益' UNION ALL
    SELECT 5, '会员功能应当如何使用？', '遵守站内规则合理使用', '用于违规内容', '权益也需遵守规则' UNION ALL
    SELECT 6, '体验到期后会？', '自动失效', '自动续期', '体验不续期' UNION ALL
    SELECT 7, '模型用量达到体验额度后？', '等待重置或按规则使用其他能力', '绕过限制', '额度受系统控制' UNION ALL
    SELECT 8, '会员中心主要用于？', '查看权益和额度', '修改他人账号', '权益信息透明展示' UNION ALL
    SELECT 9, '体验挑战通过后获得？', '7 天 TRIAL_900 体验', '永久 MAX', '奖励与挑战匹配' UNION ALL
    SELECT 10, '会员体验挑战的目的？', '了解站内会员能力', '绕过付费规则', '合理体验权益'
) q
WHERE NOT EXISTS (SELECT 1 FROM exam_question WHERE bank_id=@trial_bank AND question_order=q.source_no AND delete_state=0);

INSERT INTO growth_challenge (challenge_code, challenge_type, title, description, bank_id, question_count, passing_score, max_attempts_per_day, experience_reward, enabled, delete_state)
SELECT 'FORMAL_USER', 'FORMAL_USER', '新人试炼', '完成社区规则与安全基础题，获得正式用户资格。', @formal_bank, 10, 80, 3, 100, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM growth_challenge WHERE challenge_code = 'FORMAL_USER');
INSERT INTO growth_challenge (challenge_code, challenge_type, title, description, bank_id, question_count, passing_score, max_attempts_per_day, experience_reward, enabled, delete_state)
SELECT 'VIP_TRIAL_900', 'VIP_TRIAL_900', '会员体验挑战', '通过后获得一次 7 天 TRIAL_900 会员体验。', @trial_bank, 10, 80, 3, 80, 1, 0
WHERE NOT EXISTS (SELECT 1 FROM growth_challenge WHERE challenge_code = 'VIP_TRIAL_900');
