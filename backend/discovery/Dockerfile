FROM eclipse-temurin:21-jdk-alpine

# for the Docker compose healthcheck
RUN apk add --no-cache curl

COPY build/libs/DiscoveryServer-0.0.1-SNAPSHOT.jar discovery-server.jar

ENTRYPOINT ["java", "-jar", "/discovery-server.jar"]