USE second_hand_trade;
SET NAMES utf8mb4;

-- Reset all seedable data for the campus second-hand platform.
-- WARNING: This script clears existing business data, dictionaries, and admin data.
-- Run it only for local development or a disposable demonstration database.
-- It then seeds stable platform settings and one seller:
--   张益达 / 123456
-- with 100 ON_SALE items across every category and campus.

DELETE FROM chat_messages;
DELETE FROM chats;
DELETE FROM payments;
DELETE FROM order_status_logs;
DELETE FROM orders;
DELETE FROM reviews;
DELETE FROM favorites;
DELETE FROM item_comments;
DELETE FROM item_images;
DELETE FROM files;
DELETE FROM reports;
DELETE FROM disputes;
DELETE FROM swap_requests;
DELETE FROM wanted_posts;
DELETE FROM exchanges;
DELETE FROM purchases;
DELETE FROM notifications;
DELETE FROM announcements;
DELETE FROM audit_logs;
DELETE FROM items;
DELETE FROM user_privacy;
DELETE FROM users;
DELETE FROM system_settings;
DELETE FROM sensitive_words;
DELETE FROM category_tags;
DELETE FROM categories;
DELETE FROM admin_users;

ALTER TABLE admin_users AUTO_INCREMENT = 1;
ALTER TABLE categories AUTO_INCREMENT = 1;
ALTER TABLE category_tags AUTO_INCREMENT = 1;
ALTER TABLE sensitive_words AUTO_INCREMENT = 1;
ALTER TABLE system_settings AUTO_INCREMENT = 1;
ALTER TABLE users AUTO_INCREMENT = 1;
ALTER TABLE user_privacy AUTO_INCREMENT = 1;
ALTER TABLE items AUTO_INCREMENT = 1;
ALTER TABLE item_images AUTO_INCREMENT = 1;
ALTER TABLE favorites AUTO_INCREMENT = 1;
ALTER TABLE item_comments AUTO_INCREMENT = 1;
ALTER TABLE orders AUTO_INCREMENT = 1;
ALTER TABLE order_status_logs AUTO_INCREMENT = 1;
ALTER TABLE payments AUTO_INCREMENT = 1;
ALTER TABLE chats AUTO_INCREMENT = 1;
ALTER TABLE chat_messages AUTO_INCREMENT = 1;
ALTER TABLE purchases AUTO_INCREMENT = 1;
ALTER TABLE exchanges AUTO_INCREMENT = 1;
ALTER TABLE wanted_posts AUTO_INCREMENT = 1;
ALTER TABLE swap_requests AUTO_INCREMENT = 1;
ALTER TABLE reports AUTO_INCREMENT = 1;
ALTER TABLE disputes AUTO_INCREMENT = 1;
ALTER TABLE notifications AUTO_INCREMENT = 1;
ALTER TABLE announcements AUTO_INCREMENT = 1;
ALTER TABLE files AUTO_INCREMENT = 1;
ALTER TABLE audit_logs AUTO_INCREMENT = 1;

INSERT INTO admin_users (id, username, password_hash, role, status, last_login_at) VALUES
(1, 'admin', 'sha256$ac0e7d037817094e9e0b4441f9bae3209d67b02fa484917065f71b16109a1a78', 'SUPER_ADMIN', 'NORMAL', NULL)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), role = VALUES(role), status = VALUES(status);

INSERT INTO categories (id, name, sort_order, enabled) VALUES
(1, '教材教辅', 1, 1),
(2, '数码3C', 2, 1),
(3, '生活日用', 3, 1),
(4, '服饰鞋包', 4, 1),
(5, '运动户外', 5, 1),
(6, '其他', 6, 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), sort_order = VALUES(sort_order), enabled = VALUES(enabled);

INSERT INTO category_tags (id, category_id, name, sort_order) VALUES
(1, 1, '公共课教材', 1),
(2, 1, '考研资料', 2),
(3, 1, '四六级资料', 3),
(4, 2, '手机平板', 1),
(5, 2, '电脑配件', 2),
(6, 2, '耳机音箱', 3),
(7, 3, '宿舍电器', 1),
(8, 3, '收纳清洁', 2),
(9, 3, '床上用品', 3),
(10, 4, '箱包', 1),
(11, 4, '鞋服', 2),
(12, 4, '配饰', 3),
(13, 5, '球类', 1),
(14, 5, '健身器材', 2),
(15, 5, '骑行装备', 3),
(16, 6, '票券', 1),
(17, 6, '乐器', 2),
(18, 6, '杂物', 3)
ON DUPLICATE KEY UPDATE name = VALUES(name), sort_order = VALUES(sort_order);

INSERT INTO sensitive_words (id, word, enabled, created_by) VALUES
(1, '私下转账', 1, 1),
(2, '押金', 1, 1),
(3, '脱离平台', 1, 1),
(4, '先付款', 1, 1),
(5, '加微信交易', 1, 1),
(6, '绕过平台', 1, 1),
(7, '定金不退', 1, 1),
(8, '银行卡转账', 1, 1),
(9, '虚拟币', 1, 1),
(10, '不走平台', 1, 1)
ON DUPLICATE KEY UPDATE enabled = VALUES(enabled), created_by = VALUES(created_by);

INSERT INTO system_settings (id, setting_key, setting_value, description, updated_by) VALUES
(1, 'trade_rules', '{"maxImages":9,"disputeDays":3,"creditDeduction":10}', '平台交易规则', 1),
(2, 'payment_wechat', '{"appId":"wx-campus-demo","enabled":false}', '微信支付配置', 1),
(3, 'payment_alipay', '{"appId":"alipay-campus-demo","enabled":false}', '支付宝支付配置', 1),
(4, 'payment_campus_card', '{"merchant":"CAMPUS-2026","enabled":false}', '校园卡支付配置', 1),
(5, 'im_filter', '{"enabled":true,"blockSend":false}', 'IM敏感词过滤配置', 1),
(6, 'item_publish', '{"autoAudit":false,"maxDrafts":20}', '商品发布配置', 1),
(7, 'credit_rule', '{"defaultScore":100,"banBelow":40}', '信用分规则', 1),
(8, 'announcement_popup', '{"enabled":true,"oncePerDay":true}', '公告弹窗配置', 1),
(9, 'campus_recommend', '{"enabled":true,"defaultCampus":"校本部"}', '同校区推荐配置', 1),
(10, 'upload_policy', '{"maxFileSizeMb":10,"allowed":["jpg","png","webp"]}', '上传策略配置', 1)
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), description = VALUES(description), updated_by = VALUES(updated_by);

INSERT INTO users (
  id, student_no, password_hash, nickname, real_name, department, enrollment_year,
  campus, email, phone, avatar_url, verified_status, credit_score, status
) VALUES (
  1,
  'ZYD2026001',
  'sha256$8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92',
  '张益达',
  '张益达',
  '信息工程学院 软件工程',
  2024,
  '校本部',
  'zhangyida@school.edu.cn',
  '13800001234',
  'https://images.unsplash.com/photo-1527980965255-d3b416303d12?auto=format&fit=crop&w=320&q=80',
  'VERIFIED',
  100,
  'NORMAL'
);

INSERT INTO user_privacy (user_id, phone_visible, wechat_visible, qq, wechat) VALUES
(1, 1, 1, '123456789', 'zhangyida-campus');

CREATE TEMPORARY TABLE seed_numbers (n INT PRIMARY KEY);
INSERT INTO seed_numbers (n) VALUES
(1),(2),(3),(4),(5),(6),(7),(8),(9),(10),
(11),(12),(13),(14),(15),(16),(17),(18),(19),(20),
(21),(22),(23),(24),(25),(26),(27),(28),(29),(30),
(31),(32),(33),(34),(35),(36),(37),(38),(39),(40),
(41),(42),(43),(44),(45),(46),(47),(48),(49),(50),
(51),(52),(53),(54),(55),(56),(57),(58),(59),(60),
(61),(62),(63),(64),(65),(66),(67),(68),(69),(70),
(71),(72),(73),(74),(75),(76),(77),(78),(79),(80),
(81),(82),(83),(84),(85),(86),(87),(88),(89),(90),
(91),(92),(93),(94),(95),(96),(97),(98),(99),(100);

CREATE TEMPORARY TABLE seed_item_templates (
  template_id INT PRIMARY KEY,
  category_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description TEXT NOT NULL,
  base_price DECIMAL(10,2) NOT NULL,
  price_step INT NOT NULL,
  price_span INT NOT NULL,
  image_url VARCHAR(500) NOT NULL
);

INSERT INTO seed_item_templates VALUES
(1, 1, '高等数学同济第七版上下册', '教材干净，少量重点标注，适合期末复习和补课自学。', 22, 3, 26, 'https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=900&q=80'),
(2, 1, '考研英语真题解析套装', '近十年真题和解析册齐全，听力材料扫码可用。', 35, 4, 42, 'https://images.unsplash.com/photo-1497633762265-9d179a990aa6?auto=format&fit=crop&w=900&q=80'),
(3, 1, '四六级词汇与听力资料', '词汇书、听力练习和模拟题打包，适合暑假备考。', 18, 2, 30, 'https://images.unsplash.com/photo-1524995997946-a1c2e315a42f?auto=format&fit=crop&w=900&q=80'),
(4, 2, 'iPad Air 课堂笔记平板', '课堂记笔记使用，屏幕显示正常，附保护壳和充电线。', 1680, 53, 900, 'https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=900&q=80'),
(5, 2, '机械键盘87键茶轴', '键帽完整，灯效正常，宿舍夜间使用声音较轻。', 88, 9, 90, 'https://images.unsplash.com/photo-1618384887929-16ec33fab9ef?auto=format&fit=crop&w=900&q=80'),
(6, 2, '罗技无线鼠标', '办公学习常用，按键正常，接收器齐全。', 38, 5, 80, 'https://images.unsplash.com/photo-1527814050087-3793815479db?auto=format&fit=crop&w=900&q=80'),
(7, 2, '蓝牙降噪耳机', '通勤和自习室都能用，充电仓续航正常。', 98, 11, 160, 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80'),
(8, 3, '宿舍小冰箱52L', '毕业搬宿舍出，制冷正常，适合两人共用。', 180, 17, 220, 'https://images.unsplash.com/photo-1571175443880-49e1d25b2bc5?auto=format&fit=crop&w=900&q=80'),
(9, 3, '可折叠床上学习桌', '床上学习桌，可折叠，桌面稳定，无明显磕碰。', 18, 2, 32, 'https://images.unsplash.com/photo-1518455027359-f3f8164ba6bd?auto=format&fit=crop&w=900&q=80'),
(10, 3, '宿舍小台灯三档亮度', 'Type-C 供电，三档亮度，晚上自习够用。', 19, 3, 45, 'https://images.unsplash.com/photo-1507473885765-e6ed057f782c?auto=format&fit=crop&w=900&q=80'),
(11, 3, '收纳箱三件套', '搬宿舍多出来的收纳箱，干净无破损。', 28, 4, 55, 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=900&q=80'),
(12, 4, 'Nike运动双肩包', '容量大，可放电脑和球鞋，适合上课和短途出行。', 68, 7, 120, 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=80'),
(13, 4, '24寸旅行拉杆箱', '轮子顺滑，箱体有轻微使用痕迹，假期返乡可用。', 80, 8, 150, 'https://images.unsplash.com/photo-1581553680321-4fffae59fccd?auto=format&fit=crop&w=900&q=80'),
(14, 4, '基础款连帽卫衣', '换季整理衣柜，尺码偏宽松，面交可先看成色。', 39, 4, 90, 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?auto=format&fit=crop&w=900&q=80'),
(15, 5, '羽毛球拍双拍套装', '含两支球拍和拍套，适合新手和体育课练习。', 55, 6, 90, 'https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?auto=format&fit=crop&w=900&q=80'),
(16, 5, '山地车校内通勤自行车', '车况正常，刹车灵敏，适合校内通勤和周末骑行。', 260, 19, 360, 'https://images.unsplash.com/photo-1485965120184-e220f721d03e?auto=format&fit=crop&w=900&q=80'),
(17, 5, '瑜伽垫与弹力带组合', '宿舍健身备用，垫面干净，附弹力带。', 25, 3, 48, 'https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=900&q=80'),
(18, 6, '民谣吉他入门套装', '适合社团练习，琴弦刚换，附调音器和琴包。', 160, 13, 240, 'https://images.unsplash.com/photo-1510915361894-db8b60106cb1?auto=format&fit=crop&w=900&q=80'),
(19, 6, '校园音乐节门票', '临时有课去不了，校内面交，票面信息可核验。', 45, 5, 80, 'https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?auto=format&fit=crop&w=900&q=80'),
(20, 6, '桌面小书架与杂物篮', '桌面整理用，适合宿舍书桌和租房房间。', 24, 3, 54, 'https://images.unsplash.com/photo-1497366754035-f200968a6e72?auto=format&fit=crop&w=900&q=80');

INSERT INTO items (
  seller_id, category_id, title, description, price, original_price, condition_level,
  campus, dormitory, trade_place, trade_modes, status, swap_supported,
  view_count, favorite_count, deleted, created_at, updated_at
)
SELECT
  1,
  t.category_id,
  CONCAT(t.title, '｜', LPAD(n.n, 3, '0')),
  CONCAT(t.description, ' 编号 ', LPAD(n.n, 3, '0'), '，支持校内验货，默认当天或周末面交。'),
  ROUND(t.base_price + MOD(n.n * t.price_step, t.price_span), 2),
  ROUND((t.base_price + MOD(n.n * t.price_step, t.price_span)) * 1.65 + 19, 2),
  CASE MOD(n.n, 5)
    WHEN 0 THEN 'NEW'
    WHEN 1 THEN 'LIKE_NEW'
    WHEN 2 THEN 'GOOD'
    WHEN 3 THEN 'FAIR'
    ELSE 'GOOD'
  END,
  CASE MOD(n.n, 5)
    WHEN 0 THEN '校本部'
    WHEN 1 THEN '东校区'
    WHEN 2 THEN '西校区'
    WHEN 3 THEN '南校区'
    ELSE '大学城校区'
  END,
  CASE MOD(n.n, 6)
    WHEN 0 THEN '桃李园3栋'
    WHEN 1 THEN '东苑6栋'
    WHEN 2 THEN '西苑2栋'
    WHEN 3 THEN '南苑5栋'
    WHEN 4 THEN '榕园1栋'
    ELSE '梅园8栋'
  END,
  CASE MOD(n.n, 6)
    WHEN 0 THEN '图书馆北门'
    WHEN 1 THEN '食堂门口'
    WHEN 2 THEN '快递站旁'
    WHEN 3 THEN '体育馆入口'
    WHEN 4 THEN '教学楼大厅'
    ELSE '宿舍楼下'
  END,
  CASE WHEN MOD(n.n, 3) = 0 THEN 'OFFLINE,ESCROW' ELSE 'OFFLINE' END,
  'ON_SALE',
  CASE WHEN MOD(n.n, 4) = 0 THEN 1 ELSE 0 END,
  40 + MOD(n.n * 37, 880),
  MOD(n.n * 11, 96),
  0,
  DATE_SUB(CURRENT_TIMESTAMP, INTERVAL MOD(100 - n.n, 45) DAY),
  CURRENT_TIMESTAMP
FROM seed_numbers n
JOIN seed_item_templates t ON t.template_id = MOD(n.n - 1, 20) + 1
ORDER BY n.n;

INSERT INTO item_images (item_id, image_url, sort_order)
SELECT i.id, t.image_url, 0
FROM items i
JOIN seed_numbers n ON i.title LIKE CONCAT('%｜', LPAD(n.n, 3, '0'))
JOIN seed_item_templates t ON t.template_id = MOD(n.n - 1, 20) + 1
WHERE i.seller_id = 1;

INSERT INTO announcements (title, content, scope_type, campus, popup_enabled, status, published_at, created_by) VALUES
('校园闲置交易提醒', '面交前请确认商品成色与配件，贵重物品建议选择平台留痕沟通。', 'ALL', NULL, 1, 'PUBLISHED', CURRENT_TIMESTAMP, 1);

DROP TEMPORARY TABLE IF EXISTS seed_item_templates;
DROP TEMPORARY TABLE IF EXISTS seed_numbers;
