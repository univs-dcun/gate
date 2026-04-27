FROM eclipse-temurin:21-jdk-alpine

# for the Docker compose healthcheck
RUN apk add --no-cache curl

COPY build/libs/AuthService-0.0.1-SNAPSHOT.jar auth-service.jar

ENTRYPOINT ["java", "-jar", "/auth-service.jar"]