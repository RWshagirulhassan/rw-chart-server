FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar /app/app.jar

EXPOSE 8086
EXPOSE 8087

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
