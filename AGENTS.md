# Agent Notes

## Railway Access
- Project name: `perpetual-endurance`
- Environment: `production`
- Service: `NotesZero`
- Use a Railway project token via the `RAILWAY_TOKEN` environment variable for project-scoped CLI commands.
- Do not store or commit Railway tokens in the repo.

## Railway CLI Workflow
- Project tokens work for project-scoped commands such as `railway status`, `railway logs`, `railway redeploy`, and `railway domain`.
- Project tokens may fail with account-scoped commands like `railway whoami`, `railway list`, or `railway link`.
- When debugging a failed deploy, start with:
  - `railway status --json`
  - `railway logs --build --latest --service NotesZero --environment production --lines 200`
  - `railway logs --deployment --latest --service NotesZero --environment production --lines 200`

## Deployment Shape
- Railway deploys this repo from the root `Dockerfile`.
- The app expects a volume mounted at `/data`.
- The service should run with `SPRING_PROFILES_ACTIVE=railway`.

## Dockerfile Gotcha
- Do not use `maven:3.9.9-eclipse-temurin-25`; that tag does not exist on Docker Hub and causes Railway builds to fail.
- Use `eclipse-temurin:25-jdk` for the backend build stage and install Maven in that stage.
