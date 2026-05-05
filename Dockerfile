# ── Stage 1: Build ──────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-8 AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ────────────────────────────────────────────────────────
FROM eclipse-temurin:8-jre-alpine

WORKDIR /app
RUN addgroup -S vaultcache && adduser -S vaultcache -G vaultcache
USER vaultcache

COPY --from=builder /build/target/vaultcache-1.0.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-Xms256m", "-Xmx512m", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
