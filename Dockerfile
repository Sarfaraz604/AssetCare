FROM node:22-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package*.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /build
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src/ ./src/
COPY --from=frontend /build/frontend/dist ./frontend/dist
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=backend /build/target/assetcare-1.0.0.jar ./app.jar
# Group 0 can read the application under OpenShift's arbitrary UID policy.
RUN chgrp -R 0 /app && chmod -R g=u /app
USER 1001:0
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD wget -q -O - http://127.0.0.1:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
