# syntax=docker/dockerfile:1

# Build stage (multi-arch)
FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /app

# Leverage layer cache: download deps first
COPY pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests clean package

# Runtime stage
# NOTE: alpine variant may not be available for linux/arm64; jammy supports multi-arch.
FROM eclipse-temurin:11-jre-jammy

COPY --from=build /app/target/*.jar /usr/local/lib/1.jar

EXPOSE 9527 8443
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-Dfile.encoding=UTF-8","-jar","/usr/local/lib/1.jar"]
