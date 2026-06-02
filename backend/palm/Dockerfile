FROM eclipse-temurin:21-jdk-alpine

# for the Docker compose healthcheck
RUN apk add --no-cache curl

COPY build/libs/palm-0.0.1-SNAPSHOT.jar palm-service.jar

ENTRYPOINT ["java", "-jar", "/palm-service.jar"]