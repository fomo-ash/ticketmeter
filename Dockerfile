FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B clean package

FROM tomcat:10.1.61-jdk17-temurin
COPY --from=build /build/target/tktmeter.war /usr/local/tomcat/webapps/tktmeter.war
EXPOSE 8080
