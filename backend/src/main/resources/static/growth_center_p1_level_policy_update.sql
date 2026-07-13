-- 成长中心 P1 数据更新：按新的非线性阈值校准存量成长等级快照。

UPDATE `user_growth_profile`
SET `growth_level` = CASE
        WHEN `experience` >= 1500 THEN 6
        WHEN `experience` >= 900 THEN 5
        WHEN `experience` >= 500 THEN 4
        WHEN `experience` >= 250 THEN 3
        WHEN `experience` >= 100 THEN 2
        ELSE 1
    END,
    `update_time` = CURRENT_TIMESTAMP
WHERE `delete_state` = 0
  AND `growth_level` <> CASE
        WHEN `experience` >= 1500 THEN 6
        WHEN `experience` >= 900 THEN 5
        WHEN `experience` >= 500 THEN 4
        WHEN `experience` >= 250 THEN 3
        WHEN `experience` >= 100 THEN 2
        ELSE 1
    END;
