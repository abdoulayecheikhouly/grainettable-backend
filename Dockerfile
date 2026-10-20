FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q verify
FROM eclipse-temurin:21-jre-jammy
RUN groupadd -g 10001 shop && useradd -u 10001 -g shop -M shop && mkdir -p /data/uploads && chown -R shop:shop /data
WORKDIR /app
COPY --from=build /app/target/grainettable-api-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.awt.headless=true"
ENTRYPOINT ["java","-jar","/app/app.jar"]
