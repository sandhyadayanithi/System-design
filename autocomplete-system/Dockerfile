# ---- build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# dependencies first so they stay cached across source-only changes
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- runtime stage ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

COPY --from=build /build/target/autocomplete-system.jar app.jar

ENV REDIS_HOST=redis \
    REDIS_PORT=6379 \
    CACHE_TTL_SECONDS=300

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
