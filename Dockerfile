# One image for the API and the single-page app. Three stages; only the last one ships.
# Base images are pinned to exact tags; their digests are recorded in docs/OPERATIONS.md.

# Stage 1: the Angular production bundle (Node matches DECISIONS.md D-002).
FROM node:26.13.1-bookworm-slim AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npx ng build --configuration production

# Stage 2: the backend jar with the bundle inside it, split into layers (Maven comes from the wrapper).
FROM eclipse-temurin:21.0.13_9-jdk-noble AS build
WORKDIR /build/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
COPY --from=web /web/dist/frontend/browser src/main/resources/static
RUN ./mvnw -B -q -DskipTests package \
    && java -Djarmode=tools -jar target/lexvay-backend-*.jar extract --layers --destination /build/extracted \
       --application-filename app.jar

# The package manager caches stay in the build stages above and never reach the last one.
# Stage 3: a JRE, a non-root user, and nothing else. Files and temporary files live under /var/lexvay so the
# root filesystem can be read-only.
FROM eclipse-temurin:21.0.13_9-jre-noble AS runtime
RUN groupadd --gid 10001 lexvay \
    && useradd --uid 10001 --gid 10001 --home-dir /var/lexvay --shell /usr/sbin/nologin lexvay \
    && mkdir -p /var/lexvay/files /var/lexvay/tmp \
    && chown -R 10001:10001 /var/lexvay
WORKDIR /app
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

ENV SPRING_PROFILES_ACTIVE=prod \
    LEXVAY_PORT=8080 \
    LEXVAY_MANAGEMENT_PORT=8090 \
    LEXVAY_FILESTORE_PATH=/var/lexvay/files \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:-UsePerfData -Djava.io.tmpdir=/var/lexvay/tmp"

USER 10001:10001
VOLUME /var/lexvay
EXPOSE 8080 8090
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=3 \
    CMD curl --fail --silent --output /dev/null "http://localhost:${LEXVAY_PORT}/readyz" || exit 1
# exec so the JVM is PID 1 and receives SIGTERM, which starts the graceful shutdown.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
