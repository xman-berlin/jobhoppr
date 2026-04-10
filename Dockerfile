# ── Build stage ────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-jammy AS build
WORKDIR /workspace

# Copy pom first for dependency caching
COPY backend/pom.xml ./

# Download dependencies (cached unless pom changes)
RUN mvn dependency:go-offline -q

# Copy source and build
COPY backend/src ./src
RUN mvn package -q -DskipTests

# ── Runtime stage ───────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
