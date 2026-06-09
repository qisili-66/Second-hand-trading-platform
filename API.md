# API 文档

基础路径：`/api`

## 当前数据状态

- 普通用户默认 0 个，注册后才写入数据库。
- 管理员默认 1 个：`admin/admin123456`。
- 商品默认 0 条；初始化会清空演示商品，后续由注册用户发布，或由管理员后台代指定普通用户新增。
- 普通用户、订单、聊天、求购、置换、举报、纠纷、通知、公告等演示业务数据默认清空。
- `files` 和 `announcements` 初始化为空，商品图片和后台公告只来自真实上传或后台新增。
- 收藏、留言、订单、聊天、求购、置换初始化为空，用户操作后会真实写入对应业务表。
- 本轮新增表 `purchases`、`exchanges` 已通过 `backend/sql/03_add_purchases_exchanges.sql` 在本地数据库创建完成。
- 支付默认关闭真实收款；配置真实支付宝或微信商户参数后才会发起真实下单。

## 通用响应

成功响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

分页响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [],
    "page": 1,
    "pageSize": 10,
    "total": 20
  }
}
```

错误响应：

```json
{
  "code": 40100,
  "message": "登录已过期，请重新登录",
  "data": null
}
```

错误码约定：

| HTTP 状态 | code | 说明 |
| --- | ---: | --- |
| 400 | 40001 | 参数错误、请求体解析失败、参数类型错误 |
| 401 | 40100 | 未登录或 JWT 无效 |
| 403 | 40300 | 权限不足 |
| 404 | 40400 | 资源不存在 |
| 409 | 40900 | 数据冲突，例如学号或邮箱重复 |
| 500 | 50000 | 服务器内部错误 |

## JWT 认证

请求头：

```http
Authorization: Bearer <jwt>
```

JWT 实现：

- 算法：HMAC-SHA256
- Header：`{"alg":"HS256","typ":"JWT"}`
- Payload 字段：
  - `type`：`USER` 或 `ADMIN`
  - `id`：普通用户 ID 或管理员 ID
  - `account`：学号、邮箱或管理员账号
  - `role`：`USER` 或管理员角色
  - `iat`：签发时间
  - `exp`：过期时间

配置：

```yaml
app:
  jwt:
    secret: second-hand-trading-platform-local-dev-secret-change-me
    expire-minutes: 120
```

公开接口：

```http
POST /api/auth/register
POST /api/auth/login
POST /api/auth/admin/login
GET /api/health
GET /api/categories
GET /api/items
GET /api/items/{itemId}
GET /api/items/{itemId}/comments
GET /api/files/images/{storageKey}
GET /api/purchases
GET /api/purchases/{purchaseId}/matches
GET /api/exchanges
GET /api/exchanges/{exchangeId}/matches
```

认证规则：

- 除公开接口外，其他 `/api/**` 请求都需要 JWT。
- `/api/admin/**` 需要 `ADMIN` JWT。
- 非后台受保护接口需要 `USER` JWT。
- 未携带 token 或 token 无效返回 401。
- token 类型不匹配返回 403。

## 认证接口

### 用户注册

`POST /api/auth/register`

请求：

```json
{
  "studentNo": "20260001",
  "realName": "注册用户",
  "nickname": "注册用户",
  "department": "计算机科学与技术",
  "enrollmentYear": "2026",
  "email": "student@example.edu.cn",
  "password": "12345678",
  "phoneVisible": false,
  "wechatVisible": false
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "user": {
      "userId": 1,
      "studentNo": "20260001",
      "nickname": "注册用户",
      "realName": "注册用户",
      "email": "student@example.edu.cn",
      "department": "计算机科学与技术",
      "enrollmentYear": 2026,
      "creditScore": 100
    }
  }
}
```

### 用户登录

`POST /api/auth/login`

请求：

```json
{
  "account": "20260001",
  "password": "12345678"
}
```

`account` 支持学号或邮箱。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "<jwt>",
    "user": {
      "userId": 1,
      "studentNo": "20260001",
      "nickname": "注册用户",
      "realName": "注册用户",
      "creditScore": 100
    }
  }
}
```

### 管理员登录

`POST /api/auth/admin/login`

请求：

```json
{
  "account": "admin",
  "password": "admin123456"
}
```

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "accessToken": "<jwt>",
    "admin": {
      "adminId": 1,
      "username": "admin",
      "role": "SUPER_ADMIN",
      "status": "NORMAL"
    }
  }
}
```

## 健康检查

`GET /api/health`

公开接口。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "status": "UP",
    "service": "Second-hand-trading-platform",
    "database": "second_hand_trade",
    "timestamp": "2026-06-05T04:00:00Z"
  }
}
```

## 用户接口

### 当前用户

`GET /api/users/me`

需要 `USER` JWT。

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "userId": 1,
    "studentNo": "20260001",
    "nickname": "注册用户",
    "realName": "注册用户",
    "email": "student@example.edu.cn",
    "department": "计算机科学与技术",
    "enrollmentYear": 2026,
    "creditScore": 100
  }
}
```

未登录时不再返回空对象，统一由认证拦截器返回 401。

### 修改当前用户

`PUT /api/users/me`

需要 `USER` JWT。当前为成功占位：

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

### 我的发布

`GET /api/users/me/items`

需要 `USER` JWT。按 JWT 中的用户 ID 查询 `items.seller_id`，返回当前用户真实发布的商品。

### 我的收藏

`GET /api/users/me/favorites`

需要 `USER` JWT。按 JWT 中的用户 ID 查询 `favorites`，并返回收藏的真实商品列表。

### 我的系统通知

`GET /api/users/me/notifications`

需要 `USER` JWT。返回当前用户的 `notifications` 通知。管理员下架、重新上架或删除用户商品时会通知该商品卖家；管理员发布平台公告时会按公告范围通知目标普通用户；买家预约商品、订单状态变化会向买卖双方写入 `type = 'ORDER'` 通知；商品留言会向卖家写入 `type = 'COMMENT'` 通知。已登录用户建立 WebSocket 连接后，也会实时收到对应通知。

### 用户评价

`GET /api/users/{userId}/reviews`

需要 `USER` JWT。返回用户作为卖家收到的订单评价分页包装，读取 `reviews` 表。返回字段包含 `reviewId`、`orderId`、`orderNo`、`reviewerId`、`targetUserId`、`rating`、`content`、`createdAt`、`reviewer`、`targetUser`、`item`。

## 分类接口

### 分类列表

`GET /api/categories`

公开接口，返回启用中的一级分类。

```json
{
  "code": 0,
  "message": "success",
  "data": [
    { "categoryId": 1, "name": "教材教辅" },
    { "categoryId": 2, "name": "数码3C" },
    { "categoryId": 3, "name": "生活日用" },
    { "categoryId": 4, "name": "服饰鞋包" },
    { "categoryId": 5, "name": "运动户外" },
    { "categoryId": 6, "name": "其他" }
  ]
}
```

## 商品接口

### 商品列表

`GET /api/items`

公开接口。

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | number | 1 | 页码，从 1 开始 |
| `pageSize` | number | 10 | 每页条数，后端限制在 1 到 100 |
| `keyword` | string | - | 匹配商品标题或描述 |
| `categoryId` | number | - | 分类 ID |
| `categories` | string | - | 分类名称，多个用英文逗号分隔 |
| `conditions` | string | - | 成色，多个用英文逗号分隔 |
| `campus` | string | - | 校区 |
| `minPrice` | number | - | 最低价 |
| `maxPrice` | number | - | 最高价 |
| `sort` | string | latest | `latest`、`price_asc`、`price_desc` |

示例：

```http
GET /api/items?page=1&pageSize=10&keyword=iPad&campus=东校区&sort=price_asc
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "list": [
      {
        "itemId": 1,
        "title": "高等数学同济第七版上下册",
        "description": "教材干净，少量重点标注，适合期末复习。",
        "category": {
          "categoryId": 1,
          "name": "教材教辅"
        },
        "price": 28.00,
        "originalPrice": 68.00,
        "condition": "LIKE_NEW",
        "itemStatus": "ON_SALE",
        "campus": "校本部",
        "tradePlace": "图书馆北门",
        "swapSupported": false,
        "coverUrl": "/images/items/math-book.jpg",
        "imageUrls": ["/images/items/math-book.jpg"],
        "seller": {
          "userId": 1001,
          "nickname": "用户1001",
          "avatarUrl": "",
          "campus": "校本部"
        },
        "favoriteCount": 0,
        "viewCount": 238
      }
    ],
    "page": 1,
    "pageSize": 10,
    "total": 20
  }
}
```

当前后端已使用 MyBatis-Plus 动态条件完成筛选、排序和分页；前端列表页已切换为调用真实接口。

### 商品详情

`GET /api/items/{itemId}`

公开接口，返回指定上架商品详情。商品 ID 不存在、已删除，或状态为 `DRAFT` / `REMOVED` / `SOLD` 时返回统一 404 响应。

前端详情页“同校区相关推荐”复用 `GET /api/items?campus=当前校区`，排除当前商品后最多展示 3 个真实数据库商品；只有 1 个或 2 个时按实际数量展示，不用前端固定模板补足。

### 发布商品

`POST /api/items`

需要 `USER` JWT。真实写入 `items`，并把请求中的图片 URL 写入 `item_images`。发布页图片应先调用 `POST /api/files/images` 上传，拿到 `/api/files/images/{storageKey}` 后再作为 `imageUrls` 传入。没有传图片时后端不再复用旧图片，前端显示本地兜底图。

状态说明：

- `status: "上架"` 或不传：写入 `items.status = 'ON_SALE'`，会进入公开商品列表和详情页。
- `status: "草稿"`：写入 `items.status = 'DRAFT'`，只会在当前用户个人中心“我的发布 > 草稿”展示，公开详情接口返回 404。

请求示例：

```json
{
  "title": "高等数学教材九成新",
  "description": "教材干净，少量标注。",
  "price": 28,
  "originalPrice": 68,
  "condition": "9成新",
  "category": "教材教辅",
  "campus": "校本部",
  "dormitory": "桃李园 3 栋",
  "tradeModes": ["面交"],
  "status": "上架",
  "imageUrls": ["/images/items/math-book.jpg"]
}
```

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "itemId": 21,
    "title": "高等数学教材九成新",
    "itemStatus": "ON_SALE"
  }
}
```

### 修改商品

`PUT /api/items/{itemId}`

需要 `USER` JWT。当前返回成功占位。

### 商品上下架和软删除

以下接口需要 `USER` JWT，且只能操作当前登录用户自己发布的商品：

```http
PATCH /api/items/{itemId}/off-shelf
PATCH /api/items/{itemId}/on-shelf
DELETE /api/items/{itemId}
```

说明：

- `PATCH /off-shelf`：真实更新 `items.status = 'REMOVED'`。
- `PATCH /on-shelf`：真实更新 `items.status = 'ON_SALE'`。
- `DELETE /api/items/{itemId}`：软删除商品，真实更新 `items.deleted = 1`，并同步设为 `REMOVED`。
- 卖家只能操作自己的商品，操作别人的商品返回 403。
- 已售出商品不能下架或重新上架。
- 存在 `PENDING`、`ACCEPTED`、`PAYING`、`PAID` 进行中订单的商品不能删除。
- 兼容旧路径：`PATCH /api/items/{itemId}/remove` 等同于下架。

响应：

```json
{
  "code": 0,
  "message": "success",
  "data": true
}
```

## 收藏和评论接口

收藏写操作需要 `USER` JWT：

```http
POST /api/items/{itemId}/favorite
DELETE /api/items/{itemId}/favorite
```

留言列表为公开接口，发表留言需要 `USER` JWT：

```http
GET /api/items/{itemId}/comments
POST /api/items/{itemId}/comments
```

实现状态：

- `POST /favorite` 写入 `favorites`，重复收藏不会重复插入。
- `DELETE /favorite` 删除 `favorites` 记录，并同步扣减商品收藏数。
- `GET /comments` 查询 `item_comments`。
- `POST /comments` 写入 `item_comments`。

## 订单接口

以下接口需要 `USER` JWT：

```http
GET /api/orders
GET /api/orders/{orderId}
POST /api/orders
PATCH /api/orders/{orderId}/accept
PATCH /api/orders/{orderId}/cancel
PATCH /api/orders/{orderId}/complete
POST /api/orders/{orderId}/reviews
```

### 创建订单

`POST /api/orders`

请求：

```json
{
  "itemId": 1,
  "tradeMode": "OFFLINE",
  "message": "今天晚上图书馆门口可以交易"
}
```

说明：

- 使用 JWT 中的当前用户作为买家。
- 卖家不能购买自己发布的商品。
- 商品必须是 `ON_SALE`。
- 成功后写入 `orders`，并写入 `order_status_logs`。

### 查询订单

`GET /api/orders`

返回当前登录用户作为买家或卖家参与的订单。

`GET /api/orders/{orderId}`

返回订单详情、状态日志和支付单列表。只有订单参与人可以查看。

### 订单状态操作

```http
PATCH /api/orders/{orderId}/accept
PATCH /api/orders/{orderId}/cancel
PATCH /api/orders/{orderId}/complete
```

状态流转：

```text
PENDING -> ACCEPTED -> PAYING -> PAID -> COMPLETED
PENDING/ACCEPTED/PAYING/PAID -> CANCELLED
```

说明：

- 只有卖家可以接单。
- 买家或卖家都可以取消自己的订单，取消后未售出的商品恢复为 `ON_SALE`。
- 订单完成后商品改为 `SOLD`。
- 订单完成后，买家可以通过 `POST /api/orders/{orderId}/reviews` 对卖家评价；同一订单同一买家只能评价一次。

## 评价接口

### 创建评价

```http
POST /api/reviews
POST /api/orders/{orderId}/reviews
```

需要 `USER` JWT。两种创建方式都由后端根据订单推导 `target_user_id = orders.seller_id`，前端不能指定任意被评价用户。

请求：

```json
{
  "orderId": 12,
  "rating": 5,
  "content": "商品和描述一致，沟通顺畅"
}
```

业务规则：

- 只有已完成订单的买家可以评价该订单卖家。
- 每个订单每个买家只能评价一次。
- `rating` 必须为 1-5 星。
- `content` 最长 500 字。
- 评价成功写入 `reviews`，并自动更新卖家 `users.credit_score`。

信用分规则：

- 4-5 星：`+5`
- 3 星：`0`
- 1-2 星：`-10`
- 范围限制：`0 - 200`，后端使用 `GREATEST(0, LEAST(credit_score + ?, 200))`。

### 用户评价列表

```http
GET /api/reviews/user/{userId}
GET /api/users/{userId}/reviews
```

返回用户作为卖家收到的评价列表；`GET /api/users/{userId}/reviews` 为分页包装兼容入口。

### 用户评分统计

```http
GET /api/reviews/user/{userId}/stats
```

返回平均分、评价数、各星级数量和当前信用分。

### 创建支付单

`POST /api/orders/{orderId}/pay`

请求：

```json
{
  "provider": "ALIPAY"
}
```

`provider` 支持：

- `ALIPAY`
- `WECHAT`

成功后写入 `payments`，订单状态进入 `PAYING`。

## 聊天接口

以下接口需要 `USER` JWT：

```http
GET /api/chats
POST /api/chats
GET /api/chats/{chatId}/messages
POST /api/chats/{chatId}/messages
```

### 创建或获取会话

`POST /api/chats`

请求：

```json
{
  "itemId": 1
}
```

说明：

- 根据 `item_id + buyer_id + seller_id` 创建或获取唯一会话。
- 卖家不能和自己创建会话。
- 会话写入 `chats`。

### 会话列表

`GET /api/chats`

返回当前用户作为买家或卖家参与的会话。

### 消息列表

`GET /api/chats/{chatId}/messages`

返回会话消息分页。只有会话参与人可以查看。

### 发送消息

`POST /api/chats/{chatId}/messages`

请求：

```json
{
  "messageType": "TEXT",
  "content": "你好，这个还在吗？",
  "imageUrl": "",
  "itemId": null
}
```

说明：

- 消息写入 `chat_messages`。
- 会同步更新 `chats.last_message` 和 `chats.last_message_at`。
- 内容包含“私下转账、押金、先付款、脱离平台”等词时，后端标记 `filtered=1`。

## 支付接口

### 支付配置

配置位置：`backend/src/main/resources/application.yml`

```yaml
app:
  payment:
    return-url: http://127.0.0.1:5173/orders
    alipay:
      enabled: false
      gateway: https://openapi.alipay.com/gateway.do
      app-id:
      private-key:
      notify-url: http://127.0.0.1:8080/api/payments/alipay/notify
      return-url: http://127.0.0.1:5173/orders
    wechat:
      enabled: false
      gateway: https://api.mch.weixin.qq.com
      app-id:
      mch-id:
      merchant-serial-no:
      private-key:
      notify-url: http://127.0.0.1:8080/api/payments/wechat/notify
```

默认 `enabled: false`，调用创建支付单时会返回“支付宝支付未配置”或“微信支付未配置”，不会产生真实收款二维码。

### 支付宝回调

`POST /api/payments/alipay/notify`

支付宝异步通知入口。当前会根据 `trade_status` 和 `out_trade_no` 标记支付单为 `PAID`，并同步订单状态。

生产环境必须补支付宝公钥验签，不能直接信任回调参数。

### 微信回调

`POST /api/payments/wechat/notify`

当前接口保留路径，返回：

```json
{
  "code": "FAIL",
  "message": "微信支付回调验签和解密未配置"
}
```

生产环境必须补微信支付 API v3 回调验签和资源解密后，才能确认支付成功。

### 扫码支付的钱去哪

- 钱不会进入数据库，数据库只记录订单、支付单、流水号、支付状态和回调信息。
- 真正资金由支付宝或微信支付清结算。
- 如果配置的是平台支付宝应用或微信商户号，钱进入平台商户绑定的结算账户。
- 如果要钱直接进入卖家账户，需要卖家直连商户或分账能力。
- 平台代收模式下，还需要补资金台账、分账、提现、退款、对账和回调验签。

## WebSocket 实时推送

### 连接地址

```text
/ws
```

基于 SockJS + STOMP。前端连接时必须在 STOMP CONNECT headers 携带：

```text
Authorization: Bearer <jwt>
```

只接受普通用户 JWT。管理员后台继续使用 REST 接口。

### 允许订阅

```text
/user/queue/notifications
/user/queue/messages
/topic/broadcast
```

安全规则：

- `CONNECT` 必须携带有效 `USER` JWT。
- 只允许订阅当前用户队列和平台广播主题。
- 客户端 `SEND` 已禁用，聊天、通知、订单等写操作必须走 REST 接口，避免绕过 REST 鉴权。

推送类型：

- 订单通知：预约、接单、取消、完成、支付成功等订单状态变化推送给买卖双方。
- 聊天消息：新消息推送给会话买卖双方。
- 系统通知：管理员商品处理、公告发布、评价通知等推送给目标用户。
- 广播消息：公告发布时向 `/topic/broadcast` 推送。

## 求购与置换接口

### 求购列表

`GET /api/purchases`

公开接口。

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | number | 1 | 页码 |
| `pageSize` | number | 10 | 每页条数 |
| `keyword` | string | - | 匹配标题或描述 |
| `categoryId` | number | - | 分类 ID |
| `campus` | string | - | 校区 |
| `status` | string | OPEN | `OPEN`、`CLOSED` |

### 发布求购

`POST /api/purchases`

需要 `USER` JWT。真实写入 `purchases`。

请求：

```json
{
  "title": "求购二手自行车",
  "description": "校内通勤用，车况正常即可。",
  "categoryId": 5,
  "campus": "校本部",
  "budgetMin": 200,
  "budgetMax": 350
}
```

响应会返回求购记录，并附带 `recommendedItems`，由后端按分类、校区、预算、关键词做规则打分推荐。

### 关闭求购

`PATCH /api/purchases/{purchaseId}/close`

需要 `USER` JWT。只能关闭自己发布的求购，真实更新 `purchases.status = 'CLOSED'`。

### 求购匹配推荐

`GET /api/purchases/{purchaseId}/matches`

公开接口。返回匹配商品列表，每条包含：

- `matchScore`：匹配分。
- `matchReasons`：匹配原因，如分类一致、校区一致、价格在预算内、关键词相似。

### 发布置换

`POST /api/exchanges`

需要 `USER` JWT。真实写入 `exchanges`。

请求：

```json
{
  "itemId": 8,
  "targetItemId": 18,
  "targetCategoryId": 5,
  "expectedTitle": "运动护具或羽毛球用品",
  "description": "可补差价，同校区优先。",
  "campus": "西校区"
}
```

说明：

- `itemId` 必须是当前登录用户自己发布的在售商品。
- `targetItemId` 可选；如果填写，目标商品必须在售、不是自己的商品，并且支持置换。
- `targetCategoryId` 和 `expectedTitle` 用于匹配推荐。
- 成功后返回置换记录，并附带 `recommendedItems`。

### 置换列表

`GET /api/exchanges`

公开接口。

查询参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | number | 1 | 页码 |
| `pageSize` | number | 10 | 每页条数 |
| `keyword` | string | - | 匹配期望标题或描述 |
| `categoryId` | number | - | 目标分类 ID |
| `campus` | string | - | 校区 |
| `status` | string | OPEN | `OPEN`、`MATCHED`、`CANCELLED` |

### 置换状态

```http
PATCH /api/exchanges/{exchangeId}/matched
PATCH /api/exchanges/{exchangeId}/cancel
```

需要 `USER` JWT。只能操作自己发布的置换。

### 置换匹配推荐

`GET /api/exchanges/{exchangeId}/matches`

公开接口。返回支持置换的商品推荐，按目标商品、目标分类、校区、关键词打分。

### 兼容旧路径

旧路径仍可用，并转发到新表 `purchases`、`exchanges`：

```http
GET /api/wanted-posts
POST /api/wanted-posts
PATCH /api/wanted-posts/{postId}/close

GET /api/swap-requests
POST /api/swap-requests
PATCH /api/swap-requests/{requestId}/accept
PATCH /api/swap-requests/{requestId}/reject
PATCH /api/swap-requests/{requestId}/cancel
```

## 文件接口

### 上传图片

`POST /api/files/images`

需要 `USER` JWT。真实保存图片到后端本地目录 `app.upload.dir/images`，默认是 `uploads/images`，并写入 `files` 表。

响应示例：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "fileId": 1,
    "filename": "book.jpg",
    "storageKey": "c5014562-eab0-4715-b061-a34b645afc10.jpg",
    "url": "/api/files/images/c5014562-eab0-4715-b061-a34b645afc10.jpg",
    "sizeBytes": 128000,
    "contentType": "image/jpeg"
  }
}
```

### 读取图片

`GET /api/files/images/{storageKey}`

公开接口。商品图片使用该 URL，浏览器可直接加载，不需要额外携带 JWT。

## 管理后台接口

后台接口都需要 `ADMIN` JWT。

### 数据大盘

`GET /api/admin/dashboard`

返回真实数据库统计、近 7 日支付交易额、分类商品占比和校区商品分布：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "totalUsers": 0,
    "todayNewUsers": 0,
    "totalItems": 0,
    "onSaleItems": 0,
    "todayAmount": 0.00,
    "totalAmount": 0.00,
    "activeUsers": 0,
    "pendingVerifiedUsers": 0,
    "pendingReports": 0,
    "pendingDisputes": 0,
    "pendingOrders": 0,
    "amountTrend": [
      { "date": "05-30", "dayKey": "2026-05-30", "amount": 0.00 }
    ],
    "categoryDistribution": [
      { "categoryId": 1, "category": "教材教辅", "count": 0 }
    ],
    "campusDistribution": [
      { "campus": "校本部", "count": 0 }
    ]
  }
}
```

统计口径：

- 用户、商品、订单、支付单、举报、纠纷均从数据库实时聚合。
- 交易额按 `payments.status = 'PAID'` 的支付单统计。
- 活跃用户按当日登录、当日订单参与和当日消息发送去重统计。
- 前端后台数据大盘不再读取 `adminMock.js`，全部使用该接口返回值。

### 后台查询接口

```http
GET /api/admin/users
GET /api/admin/items
POST /api/admin/items
GET /api/admin/categories
GET /api/admin/orders
GET /api/admin/disputes
GET /api/admin/reports
GET /api/admin/settings
GET /api/admin/notices
```

说明：

- 商品管理返回当前数据库商品；`POST /api/admin/items` 可由管理员为指定普通用户新增商品。
- 分类管理返回 6 个分类。
- 订单管理返回真实订单；初始化为空，创建订单后同步展示。
- 用户、纠纷、举报、公告等清空后返回空数据或空分页。

### 后台写操作

以下接口当前保留路径，部分仍是成功占位：

```http
PATCH /api/admin/users/{userId}/disable
PATCH /api/admin/users/{userId}/enable
POST /api/admin/items
PATCH /api/admin/items/{itemId}/remove
PATCH /api/admin/items/{itemId}/off-shelf
PATCH /api/admin/items/{itemId}/on-shelf
DELETE /api/admin/items/{itemId}
POST /api/admin/categories
PUT /api/admin/categories/{categoryId}
DELETE /api/admin/categories/{categoryId}
PATCH /api/admin/disputes/{disputeId}/resolve
PATCH /api/admin/reports/{reportId}/approve
PATCH /api/admin/reports/{reportId}/reject
PUT /api/admin/settings
POST /api/admin/notices
PUT /api/admin/notices/{noticeId}
DELETE /api/admin/notices/{noticeId}
```

说明：

- 后台商品下架真实更新 `items.status = 'REMOVED'`。
- 后台商品重新上架真实更新 `items.status = 'ON_SALE'`。
- 后台商品删除真实软删除 `items.deleted = 1`。
- 后台商品下架、重新上架和删除会向商品卖家写入 `notifications.type = 'SYSTEM'` 的系统通知，并通过 WebSocket 推送。
- 买家预约商品和订单状态变化会向买卖双方写入 `notifications.type = 'ORDER'`；用户给商品留言会向卖家写入 `notifications.type = 'COMMENT'`。
- 后台新增商品需要传 `sellerId`，后续买家咨询会按该商品 `seller_id` 与真实卖家建聊。
- 后台公告新增、编辑、发布和删除真实写入 `announcements`；公告发布时会按全平台或指定校区向普通用户生成系统通知，并向 `/topic/broadcast` 广播。
- 用户禁用、分类管理、举报/纠纷处理、系统设置等仍有部分占位。

## 服务层拆分

当前后端服务层已经拆分：

商品相关核心读写已引入 `mybatis-plus-spring-boot4-starter:3.5.13`，使用 MyBatis-Plus Mapper、动态条件和分页插件；其他后台统计类查询暂时保留 `JdbcTemplate`。

- `AuthService`：注册和登录。
- `JwtService`：JWT 签发与校验。
- `UserService`：用户中心相关查询。
- `ItemService`：分类、商品筛选分页、用户发布、后台代指定卖家新增商品、收藏、留言、上下架、软删除、商品图片关联。
- `FileStorageService`：商品图片上传落盘、`files` 表记录和公开图片读取。
- `TradeWorkflowService`：订单、聊天。
- `BazaarService`：求购、置换和匹配推荐。
- `AdminService`：后台统计、举报、纠纷、设置、公告。
- `HealthService`：健康检查。

旧的 `TradeDataService` 已删除。

## 后续建议

1. 支付闭环增强：支付宝回调验签、微信回调验签和解密、退款、分账、提现账户、对账。
2. 后台分类管理改为真实写库。
3. 图片上传接入本地存储或对象存储，替换当前 URL 记录方式。
4. 求购、置换继续补详情页、我的求购/置换、站内消息通知。
5. JWT 增加 refresh token、黑名单或主动失效机制。
