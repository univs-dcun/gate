FROM eclipse-temurin:21-jdk-alpine

# for the Docker compose healthcheck
RUN apk add --no-cache curl

COPY build/libs/MatchServer-0.0.1-SNAPSHOT.jar match-server.jar

ENTRYPOINT ["java", "-jar", "/match-server.jar"]