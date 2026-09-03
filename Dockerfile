# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

# --- Runtime stage ---
# Using the -jammy (Ubuntu-based) tag instead of -alpine: it's published for
# all platforms (amd64, arm64/Apple Silicon, etc.), whereas some alpine tags
# for certain JDK versions are not, which causes
# "no match for platform in manifest" errors on ARM machines.
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN groupadd -r spring && useradd -r -g spring spring
COPY --from=build /workspace/target/task-management-service.jar app.jar
USER spring:spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
