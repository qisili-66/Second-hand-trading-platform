# 工作记录

更新时间：2026-06-05

## 当前完成状态

### 数据与登录

- 商品种子数据已删除，初始化后商品默认 0 条，后续由注册用户发布，或由管理员后台代指定普通用户新增。
- 默认普通用户为 0 个，注册后才写入数据库。
- 默认管理员保留 1 个：`admin/admin123456`。
- 注册接口真实写入 `users` 和 `user_privacy`。
- 普通用户登录从数据库校验账号密码。
- 管理员登录从 `admin_users` 校验账号密码。
- 登录成功返回 JWT。
- 前端保存后端返回的真实用户或管理员信息。

### JWT 与权限

- 已新增 `JwtService`，使用 JDK 原生 HMAC-SHA256 实现 JWT。
- 已新增统一认证拦截器 `AuthInterceptor`。
- 公开接口放行：
  - `POST /api/auth/register`
  - `POST /api/auth/login`
  - `POST /api/auth/admin/login`
  - `GET /api/health`
  - `GET /api/categories`
  - `GET /api/items`
  - `GET /api/items/{itemId}`
  - `GET /api/items/{itemId}/comments`
  - `GET /api/files/images/{storageKey}`
- `/api/admin/**` 需要 `ADMIN` JWT。
- 其他受保护接口需要 `USER` JWT。
- 拦截器会写入 `authType`、`authId`、`authAccount`、`authRole`。

### 统一异常响应

- 已新增 `GlobalExceptionHandler`。
- `ResponseStatusException`、参数错误、请求体解析失败、未知异常统一返回 `ApiResponse`。
- 错误码：
  - `40001` 参数错误
  - `40100` 未登录或 JWT 无效
  - `40300` 权限不足
  - `40400` 资源不存在
  - `40900` 数据冲突
  - `50000` 服务器内部错误

### 服务层拆分

旧的 `TradeDataService` 已删除。

当前服务：

- `AuthService`：注册、普通用户登录、管理员登录。
- `JwtService`：JWT 签发、解析和校验。
- `UserService`：当前用户、我的发布、我的收藏、用户信用分。
- `ItemService`：分类、商品筛选分页、商品发布、收藏、留言、商品图片关联。
- `FileStorageService`：商品图片上传落盘、`files` 表记录和公开图片读取。
- `TradeWorkflowService`：订单、聊天。
- `ReviewService`：订单评价、用户评价列表、评分统计、信用分联动。
- `MessageService`：通知落库、订单/聊天/系统通知和广播 WebSocket 推送。
- `BazaarService`：求购、置换和匹配推荐。
- `AdminService`：后台看板、举报、纠纷、设置、公告。
- `HealthService`：健康检查。

### MyBatis-Plus

本轮已按要求引入 MyBatis-Plus：

```xml
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
  <version>3.5.13</version>
</dependency>
<dependency>
  <groupId>com.baomidou</groupId>
  <artifactId>mybatis-plus-jsqlparser</artifactId>
  <version>3.5.13</version>
</dependency>
```

新增：

- `@MapperScan("com.example.Second_hand.trading.platform.mapper")`
- `MybatisPlusConfig`
- `PaginationInnerInterceptor`
- 实体：`ItemEntity`、`ItemImageEntity`、`FileEntity`、`FavoriteEntity`、`ItemCommentEntity`
- Mapper：`ItemMapper`、`ItemImageMapper`、`FileMapper`、`FavoriteMapper`、`ItemCommentMapper`

## 本轮接口真实化

### 1. 商品列表

接口：

```http
GET /api/items
```

已支持真实 SQL 条件：

- `keyword`
- `categoryId`
- `categories`
- `conditions`
- `campus`
- `minPrice`
- `maxPrice`
- `sort`
- `page`
- `pageSize`

实现方式：

- 使用 MyBatis-Plus 动态条件。
- 使用 MyBatis-Plus 分页插件。
- 不再查全部后由前端筛选。

### 2. 发布商品

接口：

```http
POST /api/items
```

已真实写入：

- `items`
- `item_images`
- `files`

说明：

- 需要 `USER` JWT。
- 使用当前登录用户 ID 作为 `seller_id`。
- 上架商品写入 `ON_SALE`，草稿写入 `DRAFT`。
- 如果前端没有传图片 URL，后端不再复用旧图片；前端使用兜底图展示。

### 3. 我的发布

接口：

```http
GET /api/users/me/items
```

已改为按当前 JWT 用户 ID 查询 `items.seller_id`。

当前个人中心会按 `ON_SALE`、`REMOVED`、`SOLD/RESERVED`、`DRAFT` 分栏展示，草稿可重新发布上架或删除。

### 4. 收藏

接口：

```http
POST /api/items/{itemId}/favorite
DELETE /api/items/{itemId}/favorite
GET /api/users/me/favorites
```

已完成：

- 添加收藏写入 `favorites`。
- 取消收藏删除 `favorites`。
- 同步维护 `items.favorite_count`。
- 我的收藏从 `favorites` 反查商品。

### 5. 留言

接口：

```http
GET /api/items/{itemId}/comments
POST /api/items/{itemId}/comments
```

已完成：

- 留言列表查询 `item_comments`。
- 发表留言写入 `item_comments`。
- 留言列表公开可查。
- 发表留言需要 `USER` JWT。

## 前端同步

新增：

```text
fronted/src/services/normalizers.js
```

已切换真实接口：

- `SearchResultsView.vue`：商品列表调用 `itemApi.list`。
- `PublishItemView.vue`：图片上传调用 `/api/files/images`，发布商品和保存草稿都调用 `itemApi.create`。
- `ItemDetailView.vue`：详情、收藏、留言调用真实接口。
- `ProfileCenterView.vue`：我的发布按状态分栏展示，草稿、下架和收藏调用真实接口。
- `ProductListItem.vue`、`ProductGridCard.vue`：修复价格显示。

## 文档同步

已更新：

- `README.md`
- `API.md`
- `WORK.md`

同步内容：

- MyBatis-Plus 依赖和用途。
- 商品列表真实筛选参数。
- 发布商品真实写入说明。
- 我的发布、收藏、留言真实接口说明。
- 当前剩余边界和下一步建议。

## 验证结果

后端：

```powershell
cd backend
.\mvnw.cmd test
```

结果：

- 编译通过。
- Spring 上下文加载通过。
- MyBatis-Plus 3.5.13 加载通过。
- 测试通过：1 个测试，0 失败，0 错误。

前端：

```powershell
cd fronted
npm run build
```

结果：

- 构建通过。
- 仍有 Vite/Rolldown 对 `@vueuse/core` pure annotation 的警告。
- 仍有 chunk size warning。
- 以上警告不影响运行。

服务状态：

- 本轮没有启动前端 Vite 服务。
- 本轮没有启动后端 Spring Boot 服务。

## 当前剩余边界

- 商品修改仍是成功占位。
- 后台分类管理仍未真实写库。
- JWT 暂未实现 refresh token、黑名单或主动失效机制。

## 本次追加更新：订单、聊天、支付真实化

更新时间：2026-06-05

### 订单流程

已真实化：

- `POST /api/orders`：创建订单，写入 `orders`。
- `GET /api/orders`：查询当前登录用户参与的订单。
- `GET /api/orders/{orderId}`：查询订单详情。
- `PATCH /api/orders/{orderId}/accept`：卖家接单。
- `PATCH /api/orders/{orderId}/cancel`：买家或卖家取消订单。
- `PATCH /api/orders/{orderId}/complete`：买家或卖家完成订单。
- `POST /api/orders/{orderId}/pay`：创建支付单。

订单状态流转：

```text
PENDING -> ACCEPTED -> PAYING -> PAID -> COMPLETED
PENDING/ACCEPTED/PAYING/PAID -> CANCELLED
```

已写入真实表：

- `orders`
- `order_status_logs`
- `payments`

说明：

- 创建订单使用当前 JWT 用户作为买家。
- 卖家不能购买自己的商品。
- 只有卖家可以接单。
- 订单取消会把未售出的商品恢复为 `ON_SALE`。
- 订单完成会把商品改为 `SOLD`。
- 支付回调确认后，支付单改为 `PAID`，订单同步改为 `PAID`，并写入状态日志。

### 聊天流程

已真实化：

- `GET /api/chats`：查询当前用户参与的会话。
- `POST /api/chats`：按商品创建或获取会话。
- `GET /api/chats/{chatId}/messages`：查询会话消息。
- `POST /api/chats/{chatId}/messages`：发送消息。

已写入真实表：

- `chats`
- `chat_messages`

说明：

- 买家点击商品详情的“立即咨询”会创建或获取该商品会话。
- 会话按 `item_id + buyer_id + seller_id` 唯一，`seller_id` 来自商品表，确保账号 B 咨询账号 A 商品时准确和账号 A 聊天。
- 发送消息会更新会话 `last_message` 和 `last_message_at`。
- 文本里包含“私下转账、押金、先付款、脱离平台”等词时，后端会标记 `filtered=1`。

### 支付宝和微信支付

已新增：

- `PaymentProperties`
- `PaymentService`
- `PaymentController`
- `payments` 表

支付接口：

```http
POST /api/orders/{orderId}/pay
POST /api/payments/alipay/notify
POST /api/payments/wechat/notify
```

配置位置：

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

当前默认行为：

- `enabled: false`，所以不会真的发起支付宝或微信下单。
- 没有商户参数时，调用支付接口会明确返回“支付宝支付未配置”或“微信支付未配置”。
- 这样可以避免开发环境误收款或生成不可控的真实支付二维码。

填入真实商户参数后的行为：

- 支付宝：生成 `alipay.trade.page.pay` 跳转 URL。
- 微信：调用微信支付 API v3 Native 下单，返回 `code_url` 二维码链接。
- 成功回调后写入 `payments`，并同步订单状态。

### 扫码支付的钱去哪

当前项目默认配置不会收钱，因为支付宝和微信支付都是关闭状态。

如果开启真实支付：

1. 钱不会进入数据库。
   - 数据库只记录订单、支付单、支付流水号、支付状态。
   - 真正的资金由支付宝或微信支付清结算。

2. 钱进入哪个账户，取决于你配置的商户号。
   - 如果 `app.payment.alipay.app-id/private-key` 对应平台自己的支付宝商户应用，钱进入平台商户绑定的支付宝结算账户。
   - 如果 `app.payment.wechat.mch-id` 对应平台自己的微信商户号，钱进入该微信商户号绑定的结算账户。

3. 卖家能不能直接拿到钱，取决于业务模式。
   - 平台收款模式：买家付款先进平台商户账户，平台再分账或线下结算给卖家。
   - 卖家直连模式：每个卖家都要有自己的商户号或收款身份，付款直接进卖家账户。
   - 校园二手平台更常见的是平台收款 + 分账/提现，或者纯线下面交不走平台资金。

4. 如果要做平台担保交易，需要继续补：
   - 平台资金台账。
   - 卖家提现账户绑定。
   - 支付成功回调验签。
   - 微信支付分账或支付宝分账接口。
   - 退款接口。
   - 对账单下载和差错处理。

### 前端同步

已切换真实接口：

- 商品详情页：
  - “立即咨询”创建真实聊天会话。
  - “预约商品”创建真实订单。
- 订单页：
  - 查询真实订单。
  - 接单、取消、完成调用真实接口。
  - 支付按钮调用真实支付接口。
- 聊天页：
  - 查询真实会话。
  - 查询真实消息。
  - 发送消息写入数据库。

### 验证结果

后端：

```powershell
cd backend
.\mvnw.cmd test
```

结果：通过，1 个测试，0 失败，0 错误。

前端：

```powershell
cd fronted
npm run build
```

结果：通过；仍有 Vite/Rolldown pure annotation 和 chunk size warning，不影响运行。

## 新增功能（2026-06-08）：用户评价、信用积分、实时消息推送

### 后端

- 新增 `ReviewEntity`、`ReviewMapper`、`ReviewService`、`ReviewController`。
- 复用已有 `reviews` 表，不新增数据库表；无需重新执行建表 SQL。
- `POST /api/reviews` 和 `POST /api/orders/{orderId}/reviews` 已真实创建订单评价。
- `GET /api/reviews/user/{userId}`、`GET /api/reviews/user/{userId}/stats`、`GET /api/users/{userId}/reviews` 已返回真实评价列表和评分统计。
- 评价业务规则已在后端强制校验：只有已完成订单买家可评价卖家；目标卖家由订单 `seller_id` 推导；同一订单同一买家只能评价一次；评分 1-5；内容最长 500 字。
- `UserService.updateCreditScore()` 和 `UserService.getUserCreditScore()` 已实现信用分联动：4-5 星 `+5`，3 星 `0`，1-2 星 `-10`，范围限制在 `0 - 200`。
- 新增 `NotificationEntity`、`NotificationMapper`、`MessageService`，通知统一落库并推送。
- 新增 `WebSocketConfig`，启用 SockJS + STOMP，`/ws` 连接要求 `Authorization: Bearer <jwt>`。
- 允许订阅 `/user/queue/notifications`、`/user/queue/messages`、`/topic/broadcast`；客户端 `SEND` 已禁用。
- 订单预约、接单、取消、完成、支付成功、聊天消息、管理员公告和评价通知已接入推送。

### 前端

- 新增 `OrderReview.vue`：订单完成后买家对卖家评分和文字评价。
- 新增 `ReviewList.vue`：展示卖家评价、平均分、评价数和信用分。
- 新增 `NotificationPanel.vue` 和 `services/websocket.js`：登录后连接 WebSocket 并订阅通知、聊天消息和公告广播。
- `OrdersView.vue`、`ProfileCenterView.vue`：完成订单后显示“评价卖家/评价”按钮，提交后刷新订单状态。
- `ItemDetailView.vue`：商品详情展示卖家真实评价。
- `package.json` / `package-lock.json` 已添加 `sockjs-client@1.6.1`、`stompjs@2.3.3`。
- `vite.config.js` 已添加 `/ws` 代理。

### 文档同步

- `README.md`、`API.md`、`WORK.md` 已同步用户评价、信用积分、WebSocket 推送、依赖和验证说明。

### 验证结果

后端：

```powershell
cd backend
cmd /c ""C:\Users\zhangmei\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd" -Dmaven.repo.local=C:\Users\zhangmei\.m2\repository -Dmaven.compiler.fork=true -e test"
```

结果：通过，1 个测试，0 失败，0 错误。Windows/JDK 在关闭本地 Maven 缓存 jar 的 zipfs 时仍打印 `AccessDeniedException` 诊断信息，但 Maven 最终 `BUILD SUCCESS`。

前端：

```powershell
cd fronted
npm run build
```

结果：通过；仍有 Vite/Rolldown pure annotation 和 chunk size warning，不影响运行。

## 下一步建议

下一步建议做：

```text
支付闭环增强：支付回调验签、退款、分账、提现账户和对账。
```

理由：

- 订单、聊天、支付单已经真实落库。
- 真实收款后，资金安全的核心是验签、退款、分账和对账。
- 平台如果代收，需要明确卖家结算规则和资金台账。

## 本次文档同步确认

更新时间：2026-06-05

已同步：

- `README.md`：补充订单、聊天、支付当前状态；修正“订单和聊天仍为空占位”的旧表述。
- `API.md`：补充真实订单接口、真实聊天接口、支付配置、支付回调和扫码资金流向说明。
- `WORK.md`：保留本轮商品、订单、聊天、支付真实化记录，并记录本次文档同步结果。

关于“微信和支付宝扫码给的钱去哪”：

- 默认配置不会收钱，因为支付宝和微信支付都是关闭状态。
- 开启真实支付后，钱不会进入数据库；数据库只保存订单、支付单、流水号和状态。
- 钱进入哪个账户，取决于配置的支付宝应用或微信商户号。
- 如果配置平台自己的商户号，钱进入平台商户绑定的结算账户。
- 如果要进入卖家账户，需要卖家直连商户或平台分账/提现流程。
- 后续要做真实生产收款，必须优先补回调验签、退款、分账、提现账户、资金台账和对账。

## 本次追加更新：商品上下架/删除、求购/置换真实化

更新时间：2026-06-05

### 商品上下架和软删除

已真实化：

```http
PATCH /api/items/{itemId}/off-shelf
PATCH /api/items/{itemId}/on-shelf
DELETE /api/items/{itemId}
PATCH /api/items/{itemId}/remove
```

实现说明：

- 卖家只能操作自己发布的商品。
- 下架真实更新 `items.status = 'REMOVED'`。
- 重新上架真实更新 `items.status = 'ON_SALE'`。
- 删除采用软删除，真实更新 `items.deleted = 1`，并同步设为 `REMOVED`。
- 兼容旧路径 `PATCH /api/items/{itemId}/remove`，等同于下架。
- 已售出商品不能下架或重新上架。
- 有进行中订单的商品不能删除。

后台也已真实化：

```http
PATCH /api/admin/items/{itemId}/off-shelf
PATCH /api/admin/items/{itemId}/on-shelf
DELETE /api/admin/items/{itemId}
PATCH /api/admin/items/{itemId}/remove
```

### 求购和置换

新增表：

- `purchases`
- `exchanges`

新增增量脚本：

```text
backend/sql/03_add_purchases_exchanges.sql
```

已执行结果：

```text
purchases 表已创建，当前 0 条。
exchanges 表已创建，当前 0 条。
```

真实接口：

```http
GET /api/purchases
POST /api/purchases
PATCH /api/purchases/{purchaseId}/close
GET /api/purchases/{purchaseId}/matches

GET /api/exchanges
POST /api/exchanges
PATCH /api/exchanges/{exchangeId}/matched
PATCH /api/exchanges/{exchangeId}/cancel
GET /api/exchanges/{exchangeId}/matches
```

旧路径兼容：

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

匹配推荐：

- 求购按分类、校区、预算、关键词相似度打分推荐商品。
- 置换按指定目标商品、目标分类、校区、是否支持置换、关键词相似度打分推荐商品。
- 返回字段包含 `matchScore` 和 `matchReasons`。

### 前端同步

已更新：

- `fronted/src/services/api.js`
  - 新增 `itemApi.offShelf`、`itemApi.onShelf`、`itemApi.delete`。
  - `wantedApi` 切到 `/purchases`。
  - `swapApi` 切到 `/exchanges`。
  - 管理端商品下架/删除调用真实接口。
- `fronted/src/views/admin/AdminItemsView.vue`
  - 后台商品列表从真实后台接口读取。
  - 后台商品管理支持管理员为指定普通用户新增商品。
  - 下架、批量下架、删除后刷新真实列表。
- `fronted/src/views/front/BazaarView.vue`
  - 求购列表从 `/api/purchases` 读取。
  - 发布求购真实写入 `purchases`。
  - 置换列表从 `/api/exchanges` 读取。

### 文档同步

已同步根目录文档：

- `README.md`
- `API.md`
- `DATABASE.md`
- `WORK.md`

同步内容：

- 商品下架、上架、软删除真实写库。
- 卖家只能操作自己的商品。
- 管理员后台商品新增、下架、删除真实写库。
- `purchases`、`exchanges` 新表。
- `03_add_purchases_exchanges.sql` 增量脚本。
- 求购/置换真实接口和匹配推荐规则。

### 当前剩余边界

- 商品修改 `PUT /api/items/{itemId}` 仍是成功占位。
- 求购和置换已有列表、发布、状态和匹配推荐；详情页、我的求购/置换还未补。管理员商品处理和公告发布的系统通知已补。
- 后台分类管理仍是部分占位。

## 本次追加更新：后台数据大盘真实同步

### 后端

- `GET /api/admin/dashboard` 已扩展为真实数据库聚合接口。
- 统计卡片包含用户、商品、支付交易额、活跃用户和待处理项。
- 图表数据包含近 7 日已支付交易额、分类商品占比、校区商品分布。

### 前端

- `AdminDashboardView.vue` 不再使用 `adminMock.js` 和前端 mock 商品。
- 数据卡片、ECharts 折线图、饼图、柱状图、快捷待处理入口全部读取 `adminApi.dashboard()`。

### 文档

- `README.md`、`API.md`、`DATABASE.md`、`WORK.md` 已同步后台数据大盘真实统计口径。

## 本次追加更新：管理员前台数据后台入口

- `fronted/src/App.vue`：管理员登录后在顶部“个人中心”旁显示“数据后台”入口，跳转 `/admin`。
- 普通用户不显示“数据后台”入口。
- `README.md` 已补充该入口说明和从零运行步骤。

## 本次追加更新：发布草稿、图片上传和后台公告真实化

- `PublishItemView.vue`：保存草稿真实创建 `DRAFT` 商品并跳转个人中心草稿分栏；提交发布固定创建 `ON_SALE` 商品并跳公开详情。
- `ProfileCenterView.vue`：我的发布按上架、下架、已出、草稿分栏展示；草稿和已下架商品可重新上架或删除。
- `FileStorageService`、`FileController`：图片真实保存到 `uploads/images`，`files` 表记录 `/api/files/images/{storageKey}`，图片读取接口公开可访问。
- `ItemService`：商品创建不再把数据库第一张旧图作为默认封面；没有传图片时不写 `item_images`。
- `AdminNoticesView.vue`、`AdminService`：后台公告列表、新增、编辑、发布、删除接入 `announcements` 表，不再读取实例通知数据。
- `backend/sql/02_seed_data.sql`：补充重置 `files`、`announcements`、`notifications` 等业务表自增。

## 本次追加更新：详情推荐、本人商品删除和系统通知

- `ItemDetailView.vue`：同校区相关推荐改为调用 `/api/items` 查询真实同校区商品，排除当前商品；数据库有 1 个展示 1 个、有 2 个展示 2 个、最多展示 3 个；没有真实商品时显示空状态，不再从 `mock.js` 展示固定模板。
- `ProfileCenterView.vue`：我的发布统一使用带操作按钮的商品行，上架、下架、已出、草稿商品都提供删除入口；上架商品可查看详情或下架。
- `GET /api/users/me/notifications`：新增当前用户系统通知接口。
- `ItemService`：管理员下架、重新上架或删除商品时，向该商品卖家写入 `notifications`。
- `AdminService`：管理员发布公告时，按全平台或指定校区向普通用户写入系统通知。
- `App.vue`：顶部铃铛读取系统通知；个人中心新增“系统通知”菜单。
- `.gitignore`：忽略 `backend/uploads/`，避免提交本地上传文件。

### 验证结果

后端：

```powershell
cd backend
cmd /c ""C:\Users\zhangmei\.m2\wrapper\dists\apache-maven-3.9.14\ed7edd442f634ac1c1ef5ba2b61b6d690b5221091f1a8e1123f5fadcc967520d\bin\mvn.cmd" "-Dmaven.repo.local=C:\Users\zhangmei\.m2\repository" clean test"
```

结果：通过，1 个测试，0 失败，0 错误。

前端：

```powershell
cd fronted
npm run build
```

结果：通过；仍有 Vite/Rolldown pure annotation 和 chunk size warning，不影响运行。
