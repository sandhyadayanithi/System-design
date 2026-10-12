# ---- Stage 1: Build JAR from source ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Stage 2: Runtime image ----
FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /build/target/webcrawler-*.jar app.jar

ENV SPRING_DATA_REDIS_HOST=redis \
    SPRING_DATA_REDIS_PORT=6379

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]