FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
# eclipse-temurin:17-jre is Debian-based and does NOT ship wget/curl by default --
# install curl explicitly so the docker-compose healthcheck has something to run.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /app/target/account-service-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]