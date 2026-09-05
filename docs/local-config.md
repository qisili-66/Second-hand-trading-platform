# 本地配置与提交边界

仓库只提交 `.env.example` 模板，不提交真实密钥、数据库密码、JWT 密钥、Agent 服务令牌或支付私钥。

## GitHub 模板

- `backend/.env.example`：Spring Boot 本地配置模板。
- `ai/.env.example`：AI 服务、Qdrant 和本地 BGE-M3 配置模板。

模板中的值均为占位符。复制模板为同目录下的 `.env` 后，再填入本机真实值；`.gitignore` 已忽略这两个文件。

## 本机运行

```powershell
Copy-Item backend/.env.example backend/.env
Copy-Item ai/.env.example ai/.env
```

至少需要配置：

- `backend/.env`：`DB_USERNAME`、`DB_PASSWORD`、长度不少于 32 位的 `JWT_SECRET`、`AGENT_SERVICE_TOKEN`。
- `ai/.env`：与后端相同的 `AGENT_SERVICE_TOKEN`、聊天模型的 `EXTERNAL_LLM_API_KEY`、`QDRANT_URL`、`EMBEDDING_PROVIDER=local`、`LOCAL_EMBEDDING_MODEL=BAAI/bge-m3`。

本地 Qdrant 使用 `127.0.0.1:6333`。首次使用 BGE-M3 时需要下载模型权重；权重不可用时，开发环境可由 `LOCAL_EMBEDDING_FALLBACK=true` 提供确定性回退向量。

提交前检查：

```powershell
git ls-files backend/.env ai/.env
git check-ignore -v backend/.env ai/.env
```

第一条应无输出，第二条应显示 `.gitignore` 规则。
