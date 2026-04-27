FROM eclipse-temurin:21-jdk-alpine

# for the Docker compose healthcheck
RUN apk add --no-cache curl

COPY build/libs/GateService-0.0.1-SNAPSHOT.jar gate-service.jar

ENTRYPOINT ["java", "-jar", "/gate-service.jar"]