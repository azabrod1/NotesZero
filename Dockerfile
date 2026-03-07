FROM node:20.19.5-bookworm-slim AS frontend-build
WORKDIR /workspace/frontend

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-25 AS backend-build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
COPY --from=frontend-build /workspace/frontend/dist ./src/main/resources/static
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=backend-build /workspace/target/*.jar /app/app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
