# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Build the application (skip tests; CI/verify covers them)
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre
WORKDIR /app

# OCR: native Tesseract engine + English trained data (used by Tess4J at runtime).
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

# Non-root user for safety
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/receipttrust-0.0.1-SNAPSHOT.jar app.jar

# App-local storage for uploaded images. On Render's free tier there is no
# persistent disk, so this lives inside the container (images are lost on
# restart — acceptable for free-tier testing). On paid plans, mount a disk at
# /data/storage and set RECEIPT_DIR/PROFILE_DIR to point there.
RUN mkdir -p /app/storage/receipts /app/storage/profiles /data/storage \
    && chown -R appuser:appuser /app /data
USER appuser

# Point the app at the system tessdata installed above.
ENV TESSDATA_PATH=/usr/share/tesseract-ocr/5/tessdata

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
