# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw && ./mvnw -B --no-transfer-progress dependency:resolve || true

COPY src ./src
RUN ./mvnw -B --no-transfer-progress -DskipTests clean package \
    && cp target/foodie-backend.jar /workspace/app.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S foodie && adduser -S foodie -G foodie
USER foodie

COPY --from=build /workspace/app.jar /app/app.jar

ENV SPRING_PROFILES_ACTIVE=local
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
