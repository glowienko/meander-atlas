FROM eclipse-temurin:25-jdk-jammy AS build
WORKDIR /workspace
COPY gradlew gradlew.bat settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
COPY config ./config
COPY src ./src
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar -x test \
  && cp "$(find build/libs -maxdepth 1 -name 'meander-atlas-backend-*.jar' ! -name '*-plain.jar' | head -n 1)" app.jar

FROM eclipse-temurin:25-jre-jammy
RUN useradd --system --uid 10001 --create-home app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
  CMD curl -fsS http://127.0.0.1:8080/api/v1/atlas/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
