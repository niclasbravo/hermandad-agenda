FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
ENV TZ=America/Santiago
RUN useradd -r -u 1001 agenda && mkdir -p /datos/certificados && chown agenda /datos/certificados
WORKDIR /app
COPY --from=build /app/target/agenda-hermandad.jar app.jar
USER agenda
EXPOSE 8080
ENTRYPOINT ["java", "-Duser.timezone=America/Santiago", "-jar", "/app/app.jar"]
