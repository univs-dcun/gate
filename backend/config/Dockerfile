FROM eclipse-temurin:21-jdk-alpine

# for the Docker compose healthcheck
RUN apk add --no-cache curl

COPY build/libs/ConfigServer-0.0.1-SNAPSHOT.jar config-server.jar
ENTRYPOINT ["java", "-jar", "/config-server.jar"]