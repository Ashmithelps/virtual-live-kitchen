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
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 --home-dir /app app
WORKDIR /app
COPY --from=build /app.jar app.jar
USER app
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.awt.headless=true" \
    TMPDIR=/tmp
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
