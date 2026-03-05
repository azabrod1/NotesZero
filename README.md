# Notes App (Java + React)

Structured personal notes app with:
- notebook routing (auto + clarification fallback)
- typed fact extraction
- high-risk clarification workflow
- notebook Q&A and chart-ready series APIs
- auto-updated notebook summary page revisions
- OneNote-style notebook/page UX with rich-text editing and a minimizable chat dock

## Tech Stack
- Backend: Java 15, Spring Boot 2.7, JPA, Flyway
- DB: H2 (default dev/test), PostgreSQL profile
- Frontend: React 18 + TypeScript + Vite 7 + ReactQuill

## Runtime Requirements
- Node.js `>=20.19.0` for frontend commands (`vite` requires modern Node)

## Quick Start
### Backend
```bash
mvn spring-boot:run
```
API is available at `http://localhost:8080`.

### Frontend
```bash
cd frontend
npm install
npm run dev
```
UI is available at `http://localhost:3000`.

On Windows, if your default `node` is still old, use:
```powershell
.\run-frontend.ps1 -Mode dev
```

## Tests
### Backend
```bash
mvn test
```

### Frontend
```bash
cd frontend
npm install
npm run typecheck
npm run build
```

## Profiles
- Default: in-memory H2
- PostgreSQL: `--spring.profiles.active=postgres`

## AI Provider Model
`notes.ai.provider` supports:
- `mock` (deterministic, zero cost, default)
- `anthropic` (budget-guarded, currently deterministic fallback)
- `openai` (budget-guarded, currently deterministic fallback)

`notes.ai.monthly-budget-gbp` defaults to `50.0`.

## High-Risk Clarification Policy
- Clarify only when risk is high:
  - low-confidence notebook routing
  - ambiguous fever units (F/C)
- Otherwise auto-apply extraction and routing.

## Merge Rule
- Only exact-time duplicates are considered merge candidates:
  - same notebook
  - same `occurredAt` timestamp
  - same raw note text

## Next Production Steps
- Replace fallback AI clients with real Anthropic/OpenAI adapters.
- Add OCR worker for uploaded images.
- Add authentication and multi-user tenancy.
- Add OpenAPI generation and contract tests from spec.
