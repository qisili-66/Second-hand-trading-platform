# CampusAgent 轻量服务器部署指南

本文面向 2 核 2GB / 3Mbps 左右的轻量服务器，目标是用低资源、可回滚的方式部署 CampusAgent 智能校园闲置平台。

推荐架构：Nginx 对外监听 `80/443`，前端静态文件由 Nginx 托管，`/api` 和 `/ws` 反代到 Spring Boot `127.0.0.1:8080`。AI 服务只监听 `127.0.0.1:8001`，由 Spring Boot 后端转发调用，不直接暴露公网。

## 1. 上线前准备

本地先完成验证：

```powershell
cd frontend
npm install
npm run build

cd ..\backend
.\mvnw.cmd -DskipTests package

cd ..
$env:PYTHONPATH="${PWD}\ai"
ai\.venv\Scripts\python.exe -m pytest ai\app\tests
```

轻量服务器内存有限，建议前端在本地构建后上传 `frontend/dist/`，不要每次都在服务器上跑完整前端构建。

上线前必须准备：

- 服务器 SSH 登录方式，优先使用 SSH key。
- MySQL 生产账号和强密码。
- 至少 32 位随机 `JWT_SECRET`。
- AI 服务 API key，例如千问 OpenAI-compatible key。
- 数据库和上传目录备份策略。
- 域名和 HTTPS 证书，只有 IP 也可以先跑通 HTTP。

## 2. 服务器基础环境

Debian/Ubuntu 示例：

```bash
sudo apt update
sudo apt install -y nginx mysql-server openjdk-17-jdk python3.12 python3.12-venv unzip curl
sudo timedatectl set-timezone Asia/Singapore
```

安全组建议只开放：

```text
22    SSH
80    HTTP
443   HTTPS
```

不要把 MySQL `3306`、Spring Boot `8080`、AI `8001` 开到公网。

## 3. 推荐目录结构

```text
/opt/campus-agent/
├── app/                  # 当前版本软链接或当前运行目录
├── releases/             # 每次发布一个时间戳目录，便于回滚
├── shared/
│   ├── env/              # backend.env / ai.env，不提交 Git
│   ├── logs/             # 服务日志
│   └── uploads/          # 用户上传图片
└── backups/              # 数据库和上传目录备份
```

初始化目录：

```bash
sudo mkdir -p /opt/campus-agent/{app,releases,shared/env,shared/logs,shared/uploads,backups}
sudo chown -R $USER:$USER /opt/campus-agent
```

## 4. MySQL 初始化

创建数据库和生产账号：

```bash
sudo mysql
```

```sql
CREATE DATABASE IF NOT EXISTS second_hand_trade
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'campus_agent'@'127.0.0.1' IDENTIFIED BY '换成强密码';
GRANT ALL PRIVILEGES ON second_hand_trade.* TO 'campus_agent'@'127.0.0.1';
FLUSH PRIVILEGES;
```

导入基础脚本：

```bash
mysql -ucampus_agent -p --default-character-set=utf8mb4 second_hand_trade < backend/sql/01_create_tables.sql
mysql -ucampus_agent -p --default-character-set=utf8mb4 second_hand_trade < backend/sql/02_seed_data.sql
mysql -ucampus_agent -p --default-character-set=utf8mb4 second_hand_trade < backend/sql/03_add_purchases_exchanges.sql
```

不要在生产环境执行 `backend/sql/04_reset_seed_zhangyida_100_items.sql`。这个脚本是本地演示重置数据，会清空业务数据。

## 5. 上传发布包

建议每次发布一个新目录：

```bash
RELEASE=/opt/campus-agent/releases/$(date +%Y%m%d_%H%M%S)
mkdir -p "$RELEASE"/frontend "$RELEASE"/backend "$RELEASE"/ai
```

上传内容：

```text
frontend/dist/                                      -> $RELEASE/frontend/dist/
backend/target/Second-hand-trading-platform-0.0.1-SNAPSHOT.jar -> $RELEASE/backend/
ai/                                                -> $RELEASE/ai/
backend/sql/                                       -> $RELEASE/backend/sql/
```

切换当前版本：

```bash
ln -sfn "$RELEASE" /opt/campus-agent/app
```

## 6. 环境变量

`/opt/campus-agent/shared/env/backend.env`：

```bash
DB_URL=jdbc:mysql://127.0.0.1:3306/second_hand_trade?useUnicode=true&characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=campus_agent
DB_PASSWORD=换成强密码
JWT_SECRET=换成至少32位随机字符串
APP_UPLOAD_DIR=/opt/campus-agent/shared/uploads
AI_SERVICE_BASE_URL=http://127.0.0.1:8001
AI_SERVICE_TIMEOUT_SECONDS=25
```

`/opt/campus-agent/shared/env/ai.env`：

```bash
QWEN_API_KEY=你的千问或 OpenAI-compatible API Key
QWEN_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
QWEN_MODEL=qwen3.7-max
LLM_TIMEOUT_SECONDS=18
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=second_hand_trade
DB_USER=campus_agent
DB_PASSWORD=换成强密码
```

给环境文件加权限：

```bash
chmod 600 /opt/campus-agent/shared/env/*.env
```

## 7. AI 服务依赖

```bash
cd /opt/campus-agent/app/ai
python3.12 -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
```

## 8. systemd 服务

`/etc/systemd/system/campus-backend.service`：

```ini
[Unit]
Description=CampusAgent Spring Boot backend
After=network.target mysql.service

[Service]
WorkingDirectory=/opt/campus-agent/app/backend
EnvironmentFile=/opt/campus-agent/shared/env/backend.env
ExecStart=/usr/bin/java -jar /opt/campus-agent/app/backend/Second-hand-trading-platform-0.0.1-SNAPSHOT.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

`/etc/systemd/system/campus-ai.service`：

```ini
[Unit]
Description=CampusAgent FastAPI AI service
After=network.target

[Service]
WorkingDirectory=/opt/campus-agent/app/ai
EnvironmentFile=/opt/campus-agent/shared/env/ai.env
ExecStart=/opt/campus-agent/app/ai/.venv/bin/uvicorn app.main:app --host 127.0.0.1 --port 8001
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

启用服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now campus-ai campus-backend
sudo systemctl status campus-ai campus-backend
```

查看日志：

```bash
journalctl -u campus-backend -f
journalctl -u campus-ai -f
```

## 9. Nginx 配置

`/etc/nginx/sites-available/campus-agent`：

```nginx
server {
    listen 80;
    server_name 你的域名或服务器IP;

    root /opt/campus-agent/app/frontend/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
    }

    location /ws/ {
        proxy_pass http://127.0.0.1:8080/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 60s;
    }
}
```

启用配置：

```bash
sudo ln -sfn /etc/nginx/sites-available/campus-agent /etc/nginx/sites-enabled/campus-agent
sudo nginx -t
sudo systemctl reload nginx
```

有域名后建议用 Certbot 配 HTTPS：

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d 你的域名
```

## 10. 上线验收

按顺序检查：

```bash
curl http://127.0.0.1:8080/api/health
curl http://127.0.0.1:8001/health
curl http://你的域名或IP/api/health
```

浏览器检查：

- 首页能打开，标题为 `CampusAgent 智能校园闲置平台`。
- 商品列表和商品详情能加载。
- 普通用户能登录。
- 管理员能进入 `/admin`。
- 发布商品、上传图片、收藏、留言流程可用。
- Agent buyer/seller 能返回建议或 fallback。
- 聊天入口和通知入口不报错。

## 11. 备份与回滚

发布前备份数据库和上传目录：

```bash
mysqldump -uroot -p --default-character-set=utf8mb4 second_hand_trade > /opt/campus-agent/backups/second_hand_trade_$(date +%Y%m%d_%H%M%S).sql
tar -czf /opt/campus-agent/backups/uploads_$(date +%Y%m%d_%H%M%S).tar.gz /opt/campus-agent/shared/uploads
```

回滚到上一个版本：

```bash
ln -sfn /opt/campus-agent/releases/上一个时间戳 /opt/campus-agent/app
sudo systemctl restart campus-ai campus-backend
sudo systemctl reload nginx
```

## 12. 生产注意事项

- 不要把服务器密码、数据库密码、JWT secret、AI key 写进 Git。
- 生产环境不要复用默认演示账号密码。
- 支付配置默认关闭；未完成回调验签、退款、对账、分账/提现前，不建议开启真实支付。
- `backend/scripts/init-database.ps1` 和 `04_reset_seed_zhangyida_100_items.sql` 不适合生产库。
- 2GB 内存机器建议减少服务器端构建，优先上传本地构建产物。
- 如果 Agent 响应慢，优先检查 AI 服务日志、`QWEN_API_KEY`、网络连通性和 `LLM_TIMEOUT_SECONDS`。
