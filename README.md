# Notes App (Java + React)

Structured personal notes app with:
- notebook routing (auto + clarification fallback)
- typed fact extraction
- high-risk clarification workflow
- notebook Q&A and chart-ready series APIs
- auto-updated notebook summary page revisions
- OneNote-style notebook/page UX with rich-text editing and a minimizable chat dock

## Tech Stack
- Backend: Java 25, Spring Boot 3.5, JPA, Flyway
- DB: H2 (default dev/test), PostgreSQL profile
- Frontend: React 18 + TypeScript + Vite 7 + ReactQuill

## Runtime Requirements
- JDK `25` for backend commands (`mvn ...`)
- Node.js `>=20.19.0` for frontend commands (`vite` requires modern Node)

## Quick Start
### Backend
```bash
mvn spring-boot:run
```
API is available at `http://localhost:8080`.

On Windows, to force JDK 25 even if your global `JAVA_HOME` is old:
```powershell
.\run-backend.ps1 -Mode run
```

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

Windows JDK-25-safe test command:
```powershell
.\run-backend.ps1 -Mode clean-test
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
- Railway demo deploy: `--spring.profiles.active=railway`

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

## Deploying Frontend to Vercel
1. Create a new Vercel project and set the **Root Directory** to `frontend`.
2. Add environment variable `VITE_API_BASE_URL` to your deployed backend base URL (for example `https://your-backend.example.com`).
3. Ensure your backend allows CORS from the Vercel frontend domain.
4. Deploy with:
   ```bash
   cd frontend
   npx vercel --prod
   ```

`frontend/vercel.json` includes an SPA rewrite so client-side routes resolve to `index.html`.

## Next Production Steps
- Replace fallback AI clients with real Anthropic/OpenAI adapters.
- Add OCR worker for uploaded images.
- Add authentication and multi-user tenancy.
- Add OpenAPI generation and contract tests from spec.

## Railway Deploy
Recommended POC deploy shape:
- one Railway service using the root `Dockerfile`
- one Railway volume mounted at `/data`
- `railway` Spring profile for file-backed H2 persistence

Why this shape:
- the React frontend now builds into the Spring Boot jar, so the app is served from one public URL
- `notes.ai.provider` stays on `mock`, so there is no AI API cost by default
- the Railway profile switches H2 from in-memory to file-backed storage so demo data survives restarts

### One-Time Setup
1. Push this repo to GitHub.
2. In Railway, create a new project from the GitHub repo.
3. Add a volume and mount it at `/data`.
4. Set `SPRING_PROFILES_ACTIVE=railway`.
5. Deploy and open the generated Railway domain.

### Update Flow
- make changes in this repo
- push to the tracked branch
- Railway rebuilds and redeploys automatically

### Notes
- If you skip the volume, the app still works, but the H2 database becomes ephemeral.
- If you want PostgreSQL later, switch to the `postgres` profile and provide the datasource env vars instead.
