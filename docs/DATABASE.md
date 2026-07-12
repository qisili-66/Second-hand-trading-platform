# 数据库设计

数据库：MySQL 8.x  
字符集：`utf8mb4`  
排序规则：`utf8mb4_unicode_ci`

## 0. 脚本文件

后端数据库配置：

```text
backend/src/main/resources/application.yml
```

建表脚本：

```text
backend/sql/01_create_tables.sql
```

初始化数据脚本：

```text
backend/sql/02_seed_data.sql
```

求购和置换新表增量脚本：

```text
backend/sql/03_add_purchases_exchanges.sql
```

一键执行脚本：

```text
backend/scripts/init-database.ps1
```

执行方式：

```powershell
cd backend
.\scripts\init-database.ps1
```

连接配置：

```text
host: 127.0.0.1
port: 3306
database: second_hand_trade
username: root
password: root
```

初始化数据规则：

- `users` 普通用户表初始化后为 0；脚本会清空已有普通用户和用户隐私设置。
- `admin_users` 保留 1 个管理员账号：`admin/admin123456`。
- 商品默认 0 条；初始化脚本会清空已有演示商品、商品图片和图片文件，后续由普通用户发布或管理员后台代指定普通用户新增。
- 订单、聊天、求购、置换、收藏、留言、通知、举报、纠纷、公告默认不插入演示数据。
- SQL 文件使用 `SET NAMES utf8mb4`，脚本通过 MySQL 直接读取文件，避免中文乱码。
- 本轮已执行 `backend/sql/03_add_purchases_exchanges.sql`，当前 `purchases` 和 `exchanges` 表已在本地数据库创建完成。

后台数据大盘口径：

- 用户数来自 `users`，商品数来自 `items`。
- 今日/累计交易额来自 `payments` 中 `status = 'PAID'` 的支付单。
- 近 7 日交易额按 `payments.paid_at` 聚合。
- 分类商品占比按 `categories` 左连接未删除 `items` 聚合。
- 校区分布按未删除 `items.campus` 聚合。
- 待审核实名、待处理举报、待仲裁纠纷和待处理订单分别来自 `users`、`reports`、`disputes`、`orders`。

## 1. 设计原则

- 金额字段使用 `DECIMAL(10,2)`。
- 所有业务主表使用自增 `BIGINT` 主键。
- 所有表保留 `created_at`、`updated_at`。
- 业务删除优先使用软删除字段 `deleted`。
- 状态字段使用 `VARCHAR`，便于和前后端枚举值保持一致。
- 图片、消息、日志等一对多信息拆表。

## 2. 用户与认证

### users

普通用户表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 用户 ID |
| student_no | VARCHAR(32) UNIQUE | 学号/工号 |
| password_hash | VARCHAR(255) | 密码哈希 |
| nickname | VARCHAR(64) | 昵称 |
| real_name | VARCHAR(64) | 真实姓名 |
| department | VARCHAR(128) | 院系专业 |
| enrollment_year | INT | 入学年份 |
| campus | VARCHAR(64) | 默认校区 |
| email | VARCHAR(128) UNIQUE | 校园邮箱 |
| phone | VARCHAR(32) | 手机号 |
| avatar_url | VARCHAR(500) | 头像 |
| verified_status | VARCHAR(32) | `UNVERIFIED` / `PENDING` / `VERIFIED` / `REJECTED` |
| credit_score | INT | 信用分，默认 100 |
| status | VARCHAR(32) | `NORMAL` / `BANNED` |
| last_login_at | DATETIME | 最近登录时间 |
| deleted | TINYINT(1) | 软删除 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：

- `uk_users_student_no(student_no)`
- `uk_users_email(email)`
- `idx_users_campus(campus)`
- `idx_users_status(status)`

### user_privacy

用户隐私配置表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 主键 |
| user_id | BIGINT UNIQUE | 用户 ID |
| phone_visible | TINYINT(1) | 手机是否公开 |
| wechat_visible | TINYINT(1) | 微信/QQ 是否公开 |
| qq | VARCHAR(64) | QQ |
| wechat | VARCHAR(64) | 微信 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### admin_users

管理员账号表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 管理员 ID |
| username | VARCHAR(64) UNIQUE | 管理员账号 |
| password_hash | VARCHAR(255) | 密码哈希 |
| role | VARCHAR(32) | `ADMIN` / `SUPER_ADMIN` |
| status | VARCHAR(32) | `NORMAL` / `DISABLED` |
| last_login_at | DATETIME | 最近登录 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 3. 商品与分类

### categories

商品一级分类表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 分类 ID |
| name | VARCHAR(64) | 分类名称 |
| sort_order | INT | 排序 |
| enabled | TINYINT(1) | 是否启用 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

初始分类：

- 教材教辅
- 数码3C
- 生活日用
- 服饰鞋包
- 运动户外
- 其他

### category_tags

分类子标签表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 标签 ID |
| category_id | BIGINT | 一级分类 ID |
| name | VARCHAR(64) | 标签名称 |
| sort_order | INT | 排序 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：

- `idx_category_tags_category_id(category_id)`

### items

商品表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 商品 ID |
| seller_id | BIGINT | 卖家用户 ID |
| category_id | BIGINT | 分类 ID |
| title | VARCHAR(100) | 商品标题 |
| description | TEXT | 商品描述 |
| price | DECIMAL(10,2) | 售价 |
| original_price | DECIMAL(10,2) | 原价 |
| condition_level | VARCHAR(32) | `NEW` / `LIKE_NEW` / `GOOD` / `FAIR` |
| campus | VARCHAR(64) | 校区 |
| dormitory | VARCHAR(128) | 宿舍楼/教学楼 |
| trade_place | VARCHAR(128) | 交易地点 |
| trade_modes | VARCHAR(128) | 支持交易模式，逗号分隔 |
| status | VARCHAR(32) | `ON_SALE` / `RESERVED` / `SOLD` / `REMOVED` / `DRAFT` / `VIOLATION` |
| swap_supported | TINYINT(1) | 是否支持置换 |
| view_count | INT | 浏览数 |
| favorite_count | INT | 收藏数 |
| deleted | TINYINT(1) | 软删除 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：

- `idx_items_seller_id(seller_id)`
- `idx_items_category_id(category_id)`
- `idx_items_campus(campus)`
- `idx_items_status(status)`
- `idx_items_created_at(created_at)`
- `idx_items_price(price)`

### item_images

商品图片表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 图片 ID |
| item_id | BIGINT | 商品 ID |
| image_url | VARCHAR(500) | 图片地址 |
| sort_order | INT | 排序 |
| created_at | DATETIME | 创建时间 |

索引：

- `idx_item_images_item_id(item_id)`

### favorites

收藏表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 收藏 ID |
| user_id | BIGINT | 用户 ID |
| item_id | BIGINT | 商品 ID |
| created_at | DATETIME | 收藏时间 |

约束：

- `uk_favorites_user_item(user_id, item_id)`

### item_comments

商品留言表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 留言 ID |
| item_id | BIGINT | 商品 ID |
| user_id | BIGINT | 留言用户 |
| parent_id | BIGINT NULL | 父留言，用于卖家回复 |
| content | VARCHAR(500) | 留言内容 |
| deleted | TINYINT(1) | 软删除 |
| created_at | DATETIME | 创建时间 |

索引：

- `idx_item_comments_item_id(item_id)`

## 4. 订单与评价

### orders

订单表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 订单 ID |
| order_no | VARCHAR(64) UNIQUE | 订单号 |
| item_id | BIGINT | 商品 ID |
| buyer_id | BIGINT | 买家 ID |
| seller_id | BIGINT | 卖家 ID |
| amount | DECIMAL(10,2) | 订单金额 |
| status | VARCHAR(32) | `PENDING` / `ACCEPTED` / `CANCELED` / `COMPLETED` / `DISPUTING` |
| trade_mode | VARCHAR(32) | `OFFLINE` / `ESCROW` |
| trade_code | VARCHAR(32) | 面交口令 |
| trade_qr_url | VARCHAR(500) | 面交二维码 |
| buyer_message | VARCHAR(500) | 买家留言 |
| cancel_reason | VARCHAR(500) | 取消原因 |
| completed_at | DATETIME | 完成时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：

- `uk_orders_order_no(order_no)`
- `idx_orders_buyer_id(buyer_id)`
- `idx_orders_seller_id(seller_id)`
- `idx_orders_status(status)`
- `idx_orders_created_at(created_at)`

### order_status_logs

订单状态流转日志。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 日志 ID |
| order_id | BIGINT | 订单 ID |
| from_status | VARCHAR(32) | 原状态 |
| to_status | VARCHAR(32) | 新状态 |
| operator_id | BIGINT | 操作人 |
| operator_type | VARCHAR(32) | `USER` / `ADMIN` |
| remark | VARCHAR(500) | 备注 |
| created_at | DATETIME | 创建时间 |

### reviews

交易评价表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 评价 ID |
| order_id | BIGINT | 订单 ID |
| reviewer_id | BIGINT | 评价人 |
| target_user_id | BIGINT | 被评价人 |
| rating | INT | 星级，1-5 |
| content | VARCHAR(500) | 评价内容 |
| created_at | DATETIME | 创建时间 |

约束：

- `uk_reviews_order_reviewer(order_id, reviewer_id)`

## 5. IM 聊天

### chats

会话表。

会话由商品真实卖家和咨询买家组成。后端按 `items.seller_id` 写入 `chats.seller_id`，所以账号 B 咨询账号 A 的商品时，会进入 A 与 B 的唯一会话。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 会话 ID |
| item_id | BIGINT | 商品 ID |
| buyer_id | BIGINT | 买家 ID |
| seller_id | BIGINT | 卖家 ID |
| last_message | VARCHAR(500) | 最近消息 |
| last_message_at | DATETIME | 最近消息时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

约束：

- `uk_chats_item_buyer_seller(item_id, buyer_id, seller_id)`

### chat_messages

聊天消息表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 消息 ID |
| chat_id | BIGINT | 会话 ID |
| sender_id | BIGINT | 发送人 |
| message_type | VARCHAR(32) | `TEXT` / `IMAGE` / `ITEM_CARD` |
| content | TEXT | 文本内容 |
| image_url | VARCHAR(500) | 图片消息 |
| item_id | BIGINT NULL | 商品卡片 |
| filtered | TINYINT(1) | 是否触发敏感词 |
| read_at | DATETIME NULL | 已读时间 |
| created_at | DATETIME | 创建时间 |

索引：

- `idx_chat_messages_chat_id_created_at(chat_id, created_at)`

## 6. 求购与置换

### wanted_posts

旧求购帖子表，当前保留用于兼容旧路径；新接口主表为 `purchases`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 求购 ID |
| user_id | BIGINT | 发布人 |
| title | VARCHAR(100) | 求购物品 |
| description | TEXT | 需求描述 |
| category_id | BIGINT | 分类 |
| campus | VARCHAR(64) | 需求校区 |
| budget_min | DECIMAL(10,2) | 最低预算 |
| budget_max | DECIMAL(10,2) | 最高预算 |
| status | VARCHAR(32) | `OPEN` / `CLOSED` |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### purchases

求购表，`POST /api/purchases` 真实写入本表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 求购 ID |
| user_id | BIGINT | 发布人 |
| title | VARCHAR(100) | 求购物品 |
| description | TEXT | 需求描述 |
| category_id | BIGINT | 分类 |
| campus | VARCHAR(64) | 需求校区 |
| budget_min | DECIMAL(10,2) | 最低预算 |
| budget_max | DECIMAL(10,2) | 最高预算 |
| status | VARCHAR(32) | `OPEN` / `CLOSED` |
| deleted | TINYINT(1) | 软删除 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：

- `idx_purchases_user_id(user_id)`
- `idx_purchases_category_id(category_id)`
- `idx_purchases_campus(campus)`
- `idx_purchases_status(status)`
- `idx_purchases_created_at(created_at)`

### swap_requests

旧以物换物申请表，当前保留用于兼容旧路径；新接口主表为 `exchanges`。`items.swap_supported` 表示商品是否支持置换。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 置换申请 ID |
| request_no | VARCHAR(64) UNIQUE | 置换申请编号 |
| requester_id | BIGINT | 发起人 |
| target_item_id | BIGINT | 想置换的目标商品 |
| offered_item_id | BIGINT | 发起人提供的置换商品 |
| owner_id | BIGINT | 目标商品卖家 |
| status | VARCHAR(32) | `PENDING` / `ACCEPTED` / `REJECTED` / `CANCELLED` |
| message | VARCHAR(1000) | 置换说明 |
| handled_at | DATETIME NULL | 处理时间 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：

- `idx_swap_requests_requester_id(requester_id)`
- `idx_swap_requests_target_item_id(target_item_id)`
- `idx_swap_requests_status(status)`

### exchanges

置换表，`POST /api/exchanges` 真实写入本表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 置换 ID |
| exchange_no | VARCHAR(64) UNIQUE | 置换编号 |
| user_id | BIGINT | 发布人 |
| item_id | BIGINT | 发布人用于置换的商品 |
| target_item_id | BIGINT NULL | 指定目标商品，可为空 |
| target_category_id | BIGINT NULL | 期望目标分类 |
| expected_title | VARCHAR(100) | 期望换到的物品 |
| description | TEXT | 置换说明 |
| campus | VARCHAR(64) | 交易校区 |
| status | VARCHAR(32) | `OPEN` / `MATCHED` / `CANCELLED` |
| deleted | TINYINT(1) | 软删除 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

索引：

- `idx_exchanges_user_id(user_id)`
- `idx_exchanges_item_id(item_id)`
- `idx_exchanges_target_item_id(target_item_id)`
- `idx_exchanges_target_category_id(target_category_id)`
- `idx_exchanges_status(status)`
- `idx_exchanges_created_at(created_at)`

### 匹配推荐规则

求购匹配商品时，按以下规则加权打分：

- 分类一致。
- 校区一致。
- 商品价格在预算内。
- 标题或描述关键词相似。

置换匹配商品时，按以下规则加权打分：

- 指定目标商品一致。
- 目标分类一致。
- 校区一致。
- 商品支持置换。
- 期望标题或描述关键词相似。

## 7. 风控、举报与纠纷

### reports

举报表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 举报 ID |
| reporter_id | BIGINT | 举报人 |
| target_type | VARCHAR(32) | `ITEM` / `USER` / `MESSAGE` |
| target_id | BIGINT | 被举报对象 ID |
| report_type | VARCHAR(64) | 虚假商品/欺诈/违规内容 |
| content | VARCHAR(1000) | 举报说明 |
| status | VARCHAR(32) | `PENDING` / `APPROVED` / `REJECTED` |
| handled_by | BIGINT NULL | 处理管理员 |
| handled_at | DATETIME NULL | 处理时间 |
| result_remark | VARCHAR(1000) | 处理备注 |
| created_at | DATETIME | 创建时间 |

### disputes

纠纷表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 纠纷 ID |
| dispute_no | VARCHAR(64) UNIQUE | 纠纷编号 |
| order_id | BIGINT | 订单 ID |
| applicant_id | BIGINT | 申请人 |
| reason | VARCHAR(1000) | 纠纷原因 |
| evidence_urls | TEXT | 证据图片 JSON |
| status | VARCHAR(32) | `PENDING` / `PROCESSING` / `REFUND_APPROVED` / `REJECTED` |
| handled_by | BIGINT NULL | 仲裁管理员 |
| handled_at | DATETIME NULL | 仲裁时间 |
| result_remark | VARCHAR(1000) | 仲裁说明 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### sensitive_words

敏感词表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 主键 |
| word | VARCHAR(128) UNIQUE | 敏感词 |
| enabled | TINYINT(1) | 是否启用 |
| created_by | BIGINT | 创建管理员 |
| created_at | DATETIME | 创建时间 |

### audit_logs

后台操作审计日志。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 日志 ID |
| admin_id | BIGINT | 管理员 ID |
| action | VARCHAR(128) | 操作 |
| target_type | VARCHAR(64) | 操作对象类型 |
| target_id | BIGINT | 操作对象 ID |
| detail | TEXT | 操作详情 |
| ip | VARCHAR(64) | IP |
| created_at | DATETIME | 创建时间 |

## 8. 公告、通知与系统配置

### announcements

平台公告表。

初始化脚本会清空该表。管理员后台新增、保存草稿、发布和删除公告都会真实写入或删除 `announcements`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 公告 ID |
| title | VARCHAR(150) | 标题 |
| content | TEXT | 内容 |
| scope_type | VARCHAR(32) | `ALL` / `CAMPUS` |
| campus | VARCHAR(64) NULL | 指定校区 |
| popup_enabled | TINYINT(1) | 是否弹窗 |
| status | VARCHAR(32) | `DRAFT` / `PUBLISHED` / `OFFLINE` |
| published_at | DATETIME NULL | 发布时间 |
| created_by | BIGINT | 创建管理员 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### notifications

用户通知表。

当前用于系统通知：管理员处理用户商品、管理员发布平台公告时写入。前端顶部铃铛和个人中心“系统通知”读取该表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 通知 ID |
| user_id | BIGINT | 用户 ID |
| type | VARCHAR(64) | 通知类型 |
| title | VARCHAR(150) | 标题 |
| content | VARCHAR(1000) | 内容 |
| read_at | DATETIME NULL | 已读时间 |
| created_at | DATETIME | 创建时间 |

索引：

- `idx_notifications_user_read(user_id, read_at)`

### system_settings

系统配置表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 配置 ID |
| setting_key | VARCHAR(128) UNIQUE | 配置键 |
| setting_value | TEXT | 配置值 JSON |
| description | VARCHAR(500) | 说明 |
| updated_by | BIGINT | 更新管理员 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

## 9. 文件资源

### files

上传文件表。

商品发布页上传图片时，后端把文件保存到 `app.upload.dir/images`，默认 `uploads/images`，并在本表记录访问地址 `/api/files/images/{storage_key}`。初始化脚本会清空 `files` 表；本地磁盘上的旧上传文件如需清理，可手动删除 `uploads/images`。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | BIGINT PK AI | 文件 ID |
| owner_id | BIGINT | 上传用户 |
| file_type | VARCHAR(32) | `IMAGE` / `AVATAR` / `EVIDENCE` |
| original_name | VARCHAR(255) | 原文件名 |
| storage_key | VARCHAR(500) | 存储 key |
| url | VARCHAR(500) | 访问地址 |
| size_bytes | BIGINT | 文件大小 |
| content_type | VARCHAR(128) | MIME 类型 |
| created_at | DATETIME | 创建时间 |

## 10. 建议建表顺序

1. `users`
2. `admin_users`
3. `user_privacy`
4. `categories`
5. `category_tags`
6. `items`
7. `item_images`
8. `favorites`
9. `item_comments`
10. `orders`
11. `order_status_logs`
12. `reviews`
13. `chats`
14. `chat_messages`
15. `wanted_posts`
16. `purchases`
17. `swap_requests`
18. `exchanges`
19. `reports`
20. `disputes`
21. `sensitive_words`
22. `announcements`
23. `notifications`
24. `system_settings`
25. `files`
26. `audit_logs`
