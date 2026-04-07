# ── Build stage ────────────────────────────────────────────────────────────────
FROM gradle:8.10.2-jdk21-jammy AS build
WORKDIR /workspace

# Copy build files first for dependency caching
COPY backend/build.gradle backend/settings.gradle ./

# Download dependencies (cached unless build files change)
RUN gradle dependencies --no-daemon -q || true

# Copy source and build
COPY backend/src ./src
RUN gradle bootJar --no-daemon -q

# ── Runtime stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
