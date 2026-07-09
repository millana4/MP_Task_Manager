# --- Стадия сборки: собираем jar из исходников через Gradle ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Сначала копируем только файлы сборки — чтобы Docker кэшировал скачивание
# зависимостей и не перекачивал их при каждом изменении кода.
COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Теперь копируем исходники и собираем.
COPY src src
RUN ./gradlew bootJar --no-daemon -x test

# --- Стадия запуска: только JRE + готовый jar, без Gradle и исходников ---
FROM eclipse-temurin:21-jre
WORKDIR /app

# Копируем собранный jar из стадии сборки.
COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]