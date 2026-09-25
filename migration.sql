-- ============================================


-- ============================================


ALTER TABLE user ADD COLUMN email VARCHAR(100);


ALTER TABLE user ADD COLUMN password VARCHAR(128);


ALTER TABLE user ADD UNIQUE INDEX idx_email (email);


INSERT INTO user (email, password, openid, name, phone, sex, id_number, avatar, create_time)
VALUES ('test@example.com', 'e10adc3949ba59abbe56e057f20f883e', 'test_openid_001', 'Test User', '13800000000', '1', '110101199001011234',
        'https://sky-take-out.oss-cn-beijing.aliyuncs.com/default_avatar.png',
        NOW());


SELECT 'Migration completed!' AS message;
