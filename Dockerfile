# ===================================================
# Stage 1: BUILD
# Image Maven + JDK pour compiler l'application
# ===================================================
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

WORKDIR /app

# Copier le pom.xml seul pour profiter du cache des dépendances
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copier le code source et compiler
COPY src ./src
RUN mvn clean package -DskipTests -B

# ===================================================
# Stage 2: RUN
# Image JRE légère pour l'exécution
# ===================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# Sécurité : utilisateur non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copier seulement le JAR depuis le builder
COPY --from=builder /app/target/*.jar app.jar

# Donner les droits à l'utilisateur applicatif
RUN chown -R appuser:appgroup /app

USER appuser

# Variables d'environnement par défaut
ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

# Healthcheck intégré
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
