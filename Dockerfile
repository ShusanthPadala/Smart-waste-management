# ---- Build stage: compile and package the Spring Boot jar ----
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Resolve dependencies first so they are cached between builds.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -ntp -q dependency:go-offline

COPY src src
# Tests run in GitHub Actions; skip them here to keep deploys fast.
RUN ./mvnw -B -ntp -q package -DskipTests && cp target/*.jar app.jar

# ---- Runtime stage: a slim JRE image ----
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
RUN useradd --system --uid 10001 app
COPY --from=build /app/app.jar app.jar
USER app

# Sized for small instances (e.g. Render's free tier: 512 MB RAM, 0.1 CPU).
# Override JAVA_OPTS in the host's environment settings if you have more memory.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

# Most hosts set PORT; the app falls back to 8080 when it isn't set.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
