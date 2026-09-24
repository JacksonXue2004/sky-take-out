-- ============================================
-- Sky Take-Out 数据库迁移脚本
-- 用于适配美国求职市场（邮箱登录 + Stripe 支付）
-- ============================================

-- 重要：请按顺序执行，如果某一步报错请跳过继续执行后面的步骤

-- 步骤 1: 给 user 表添加 email 字段
-- 如果报错 "Duplicate column name 'email'"，说明字段已存在，跳过此步
ALTER TABLE user ADD COLUMN email VARCHAR(100) COMMENT '邮箱地址（用于登录）';

-- 步骤 2: 给 user 表添加 password 字段
-- 如果报错 "Duplicate column name 'password'"，说明字段已存在，跳过此步
ALTER TABLE user ADD COLUMN password VARCHAR(128) COMMENT '密码（MD5 加密）';

-- 步骤 3: 添加 email 唯一索引
-- 如果报错 "Duplicate key name 'idx_email'"，说明索引已存在，跳过此步
ALTER TABLE user ADD UNIQUE INDEX idx_email (email);

-- 步骤 4: 插入测试账号
-- 密码是 password123 的 MD5: e10adc3949ba59abbe56e057f20f883e
-- 如果报错 "Duplicate entry"，说明账号已存在，跳过此步
INSERT INTO user (email, password, openid, name, phone, sex, id_number, avatar, create_time)
VALUES ('test@example.com', 'e10adc3949ba59abbe56e057f20f883e', 'test_openid_001', 'Test User', '13800000000', '1', '110101199001011234',
        'https://sky-take-out.oss-cn-beijing.aliyuncs.com/default_avatar.png',
        NOW());

-- 完成
SELECT 'Migration completed!' AS message;
