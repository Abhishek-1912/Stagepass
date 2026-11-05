# syntax=docker/dockerfile:1

# ---- Build ----
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package && cp target/stagepass-api-*.jar app.jar

# ---- Run ----
FROM eclipse-temurin:17-jre
WORKDIR /app
RUN groupadd --system stagepass && useradd --system --gid stagepass stagepass
COPY --from=build /workspace/app.jar app.jar
USER stagepass
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
