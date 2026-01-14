\
# -------- build stage --------
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

# -------- runtime stage --------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Where SQLite file will be stored
ENV SQLITE_PATH=/data/bot.db

# Where media files (1.mp4, 2.mp4, etc.) are stored
ENV MEDIA_DIR=/app/media
RUN mkdir -p /app/media

# Persist DB
VOLUME ["/data"]

COPY --from=build /app/target/ekaterina-tax-bot-1.0.0.jar /app/bot.jar

# Required at runtime:
#   BOT_TOKEN
# Optional:
#   BOT_USERNAME, ADMIN_IDS, SQLITE_PATH, MEDIA_DIR
ENTRYPOINT ["java", "-jar", "/app/bot.jar"]
