FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --create-home --home-dir /app app
WORKDIR /app
COPY --chown=app:app --from=build /app/target/taller-api-0.0.1.jar app.jar
COPY --chown=app:app data ./data
EXPOSE 8080
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
