# Campus Trade AI Agents

Python + FastAPI + LangChain service for the campus second-hand trading platform.

## Agents

- Buyer Agent: understands natural-language buying needs, recommends items, explains risks, suggests bargain ranges, drafts chat messages or wanted posts, and creates swap drafts when the user asks to exchange items.
- Seller Agent: turns a short selling note into a publish draft with title, description, category, condition, price range, trade place, swap support, and risk tips.

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

Do not commit `.env`. Put the real Qwen key only in your local environment.

If `py -3.12` is unavailable, install Python 3.12 from the official Python installer and create the virtual environment with that executable. Avoid using the machine default `python` if it points to Python 3.14.

## Environment

- `QWEN_API_KEY`: Qwen API key. Required only for LLM-enhanced output.
- `QWEN_BASE_URL`: OpenAI-compatible Qwen endpoint.
- `QWEN_MODEL`: defaults to `qwen3.7-max`.
- `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`: optional MySQL connection for live item search.

When `QWEN_API_KEY` or LangChain dependencies are unavailable, the service returns deterministic rule-based fallback results so the platform remains demoable.
