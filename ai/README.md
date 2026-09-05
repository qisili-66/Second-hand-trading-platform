# Campus Trade AI Agents

Python + FastAPI + LangChain service for the campus second-hand trading platform.

## Agents

- Buyer Agent: understands natural-language buying needs, searches on-sale items, verifies real-time item status, reads permitted public seller-credit summaries, queries only the current user's orders and preferences, then returns explainable recommendations.

The current delivery contains only this read-only buyer capability. It cannot send private messages, create orders, publish items/wanted posts/swaps, handle disputes, access payments, or connect directly to MySQL. Business data is obtained only through Spring's authenticated internal read-only tool gateway.

## Quick Start

Use Python 3.12. Python 3.14 may break FastAPI/Pydantic/LangChain dependency compatibility on this project.

From the project root on Windows, you can start the local AI service with:

```bat
ai\start.bat
```

The script creates `ai\.venv`, installs dependencies on the first successful run, creates `ai\.env` from `.env.example` if missing, and starts `http://127.0.0.1:8001`.
After dependencies are installed, it writes `ai\.venv\.requirements-installed`; later starts skip `pip install`.

```powershell
cd ai
py -3.12 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements.txt
copy .env.example .env
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

Do not commit `.env`. Put the real Agnes key only in your local `ai/.env` file.

If `py -3.12` is unavailable, install Python 3.12 from the official Python installer and create the virtual environment with that executable. Avoid using the machine default `python` if it points to Python 3.14.

## Environment

- `EXTERNAL_LLM_API_KEY`: Agnes API key. Required only for LLM-enhanced output.
- `EXTERNAL_LLM_BASE_URL`: OpenAI-compatible Agnes endpoint. Defaults to `https://apihub.agnes-ai.com/v1`.
- `EXTERNAL_LLM_MODEL`: defaults to `agnes-2.0-flash`.
- `BACKEND_BASE_URL` and `AGENT_SERVICE_TOKEN`: Spring internal read-only tool gateway settings.

When `EXTERNAL_LLM_API_KEY` or LangChain dependencies are unavailable, the service returns deterministic rule-based fallback results so the platform remains demoable.

## Trust, permissions, and audit

- The browser authenticates with JWT. Spring uses the JWT `authId` and ignores any `userId` submitted by the browser.
- Spring and FastAPI authenticate internal requests with the same local-only `AGENT_SERVICE_TOKEN`; never expose it to the browser.
- Before a recommendation is saved, Spring rechecks that the item is visible, still `ON_SALE`, and has not changed price.
- Every run records its status and a redacted tool timeline. Failed tool or service calls are recorded as failed steps.
