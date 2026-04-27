FROM eclipse-temurin:21-jdk-alpine

# for the Docker compose healthcheck
RUN apk add --no-cache curl

COPY build/libs/FaceService-0.0.1-SNAPSHOT.jar face-service.jar

ENTRYPOINT ["java", "-jar", "/face-service.jar"]