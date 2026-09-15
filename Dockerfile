FROM node:20.19.5-bookworm-slim AS frontend
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM gradle:8.14.3-jdk17 AS backend
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY src/main ./src/main
COPY --from=frontend /app/frontend/dist ./src/main/resources/static
RUN --mount=type=cache,target=/home/gradle/.gradle gradle bootJar --no-daemon

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=backend /app/build/libs/yubaba.jar /app/yubaba.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/yubaba.jar"]
