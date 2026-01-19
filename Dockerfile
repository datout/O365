# syntax=docker/dockerfile:1

# Build stage (Java 11)
FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /app

# Leverage layer cache: download deps first
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests clean package

# Runtime stage (Alpine is significantly smaller than Ubuntu-based variants)
# If you run into libc-related issues, switch back to: eclipse-temurin:11-jre-jammy
FROM eclipse-temurin:11-jre-alpine
WORKDIR /app

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 9527 8443
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-Dfile.encoding=UTF-8","-jar","/app/app.jar"]
