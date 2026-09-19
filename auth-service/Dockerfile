# Multi-stage build: compile with Maven + JDK, run with a slim JRE only.
# Image JDK major version MUST match <java.version> in pom.xml (currently 25) —
# building bytecode for a newer release than the runtime JRE supports fails
# with "class file version X, this version of the Java Runtime only
# recognizes class file versions up to Y".
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN mkdir -p /data
COPY --from=build /build/target/auth-service-*.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]
