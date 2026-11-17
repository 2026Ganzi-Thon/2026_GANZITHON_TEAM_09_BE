# syntax=docker/dockerfile:1
# 행운핑 API 이미지. 서버(VM)에서 `deploy/deploy.sh`가 빌드한다.
# 테스트는 CI가 돌리므로 여기서는 jar만 만든다.

FROM eclipse-temurin:25-jdk-noble AS build
WORKDIR /src

# 의존성 내려받기를 소스와 따로 둬서, 소스만 바뀌면 이 층은 캐시를 쓴다.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.lockfile settings-gradle.lockfile ./
COPY gradle gradle
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar \
 && cp "$(ls build/libs/*.jar | grep -v -- '-plain.jar')" /src/app.jar


FROM eclipse-temurin:25-jre-noble
# root로 돌리지 않는다.
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /app
COPY --from=build /src/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
