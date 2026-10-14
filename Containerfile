FROM docker.io/library/eclipse-temurin:21-jre
WORKDIR /app
COPY target/smart-ticket-1.0.0.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
