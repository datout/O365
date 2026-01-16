# Build stage
FROM maven:3.9.9-eclipse-temurin-11 AS build
WORKDIR /app
COPY pom.xml ./
COPY src ./src
RUN mvn -f ./pom.xml clean package

# Package stage
FROM eclipse-temurin:11-jre-jammy
COPY --from=build /app/target/*.jar /usr/local/lib/1.jar

EXPOSE 9527 8443
ENTRYPOINT ["java","-jar","/usr/local/lib/1.jar"]
