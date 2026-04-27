FROM eclipse-temurin:21-jdk-alpine

# for the Docker compose healthcheck
RUN apk add --no-cache curl

COPY build/libs/GatewayServer-0.0.1-SNAPSHOT.jar gateway-server.jar

ENTRYPOINT ["java", "-jar", "/gateway-server.jar"]