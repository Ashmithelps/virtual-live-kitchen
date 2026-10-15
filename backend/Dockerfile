# syntax=docker/dockerfile:1

# ── build ────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21.0.12_8-jdk-noble AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B package -DskipTests \
    && cp target/virtual-kitchen-backend-*.jar /app.jar

# ── runtime ──────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21.0.12_8-jre-noble
# ffmpeg/ffprobe render the order clips; fontconfig + DejaVu give Java2D real fonts for the overlay.
# curl is for the container health check.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg fontconfig fonts-dejavu-core curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
# Set by CI (the git SHA); reported to Sentry as the release and in JSON logs.
ARG APP_VERSION=dev
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true" \
    TMPDIR=/tmp \
    APP_VERSION=${APP_VERSION}
EXPOSE 8080
# Liveness only (no database): a slow database must not get the container restarted mid-migration.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=4 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health/liveness >/dev/null || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
