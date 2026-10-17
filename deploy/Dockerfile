# Builds the API jar and runs it on Java 25. Build context is the repo root (see docker-compose.yml).

FROM eclipse-temurin:25-jdk AS build
WORKDIR /src
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Download dependencies in their own layer so code-only changes rebuild quickly.
RUN sh mvnw -B -q dependency:go-offline || true
COPY src src
RUN sh mvnw -B -q package -DskipTests && cp target/app-*.jar /app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
