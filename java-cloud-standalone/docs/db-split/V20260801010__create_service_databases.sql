-- 本地/单机：同一 MySQL 实例内按服务建库 + 独立账号（生产可再拆实例）
-- 在具备足够权限的账号下执行（如 root）

CREATE DATABASE IF NOT EXISTS forum_auth_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_content_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_im_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_game_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_economy_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS forum_ai_db DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'forum_auth'@'%' IDENTIFIED BY 'forum_auth_pass';
CREATE USER IF NOT EXISTS 'forum_content'@'%' IDENTIFIED BY 'forum_content_pass';
CREATE USER IF NOT EXISTS 'forum_im'@'%' IDENTIFIED BY 'forum_im_pass';
CREATE USER IF NOT EXISTS 'forum_game'@'%' IDENTIFIED BY 'forum_game_pass';
CREATE USER IF NOT EXISTS 'forum_economy'@'%' IDENTIFIED BY 'forum_economy_pass';
CREATE USER IF NOT EXISTS 'forum_ai'@'%' IDENTIFIED BY 'forum_ai_pass';

GRANT ALL PRIVILEGES ON forum_auth_db.* TO 'forum_auth'@'%';
GRANT ALL PRIVILEGES ON forum_content_db.* TO 'forum_content'@'%';
GRANT ALL PRIVILEGES ON forum_im_db.* TO 'forum_im'@'%';
GRANT ALL PRIVILEGES ON forum_game_db.* TO 'forum_game'@'%';
GRANT ALL PRIVILEGES ON forum_economy_db.* TO 'forum_economy'@'%';
GRANT ALL PRIVILEGES ON forum_ai_db.* TO 'forum_ai'@'%';

FLUSH PRIVILEGES;
