# Backend image. Build from the repository root (the pinned OpenDB snapshot is fetched by scripts/):
#   docker build -f backend/Dockerfile -t platform-api .
# Runtime configuration: DATABASE_URL (jdbc:postgresql://... or postgresql://user:pass@host/db), DATABASE_USERNAME,
# DATABASE_PASSWORD, FRONTEND_SHARED_SECRET,
# EXAMPLE_PRICES, TRUSTED_PROXIES, PORT.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src src
RUN ./mvnw -B -q package -DskipTests

FROM alpine:3.22 AS opendb
RUN apk add --no-cache bash curl tar
WORKDIR /repo
COPY scripts/fetch-opendb.sh scripts/fetch-opendb.sh
RUN bash scripts/fetch-opendb.sh

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /src/target/platform-api-*.jar app.jar
COPY --from=opendb /repo/data/opendb /opt/opendb
# The first start imports the snapshot (a few minutes); later starts skip it when nothing changed.
ENV OPENDB_SNAPSHOT_DIR=/opt/opendb \
    OPENDB_INGEST_ON_STARTUP=true \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
