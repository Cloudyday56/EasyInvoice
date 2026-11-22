# EasyInvoice

An Accounts Payable automation pipeline.

Tech stack:
```
backend/    Java 21 · Spring Boot 4 · Spring Data JPA (Hibernate) · PostgreSQL
frontend/   React + TypeScript · Vite
sample-data/       sample dataset (CSV history, inbox emails, incoming invoice files)
```

To put in .env:
```bash
LLM_API_KEY= #Optional
LLM_MODEL= #Optional
LLM_BASE_URL= #Optional

EXTRACTOR=[manual/llm]
SEED_LENIENT=[true/false]
DATA_PATH=./sample-data
```

## Run it (one command)

```bash
docker compose up --build
```

Three containers start:

| Container | URL | What |
|---|---|---|
| `frontend` | http://localhost:5173 | React build served by nginx; `/api` is proxied to the backend |
| `backend` | http://localhost:8080 | Spring Boot API + pipeline runner |
| `db` | localhost:5432 | Postgres 16 (db/user/password `easyinvoice`) |
