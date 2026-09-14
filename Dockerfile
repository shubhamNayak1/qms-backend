FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/qms-backend-1.0.0.jar app.jar
EXPOSE 8080

# Render Starter tier (512 MB) — JDK 17 defaults to MaxRAMPercentage=25%,
# which caps the heap at ~128 MB and forces Spring Boot to spill into
# metaspace / native heap until the container OOMs. Explicitly right-size
# the JVM to the container:
#   MaxRAMPercentage=75      → ~384 MB heap ceiling on a 512 MB pod
#   MaxMetaspaceSize=128m    → hard cap so metaspace can't grow unbounded
#   Xss256k                  → thread stack (default 1 MB × 200 Tomcat
#                              threads = 200 MB just idling)
#   ExitOnOutOfMemoryError   → die loud instead of thrashing indefinitely
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:MaxMetaspaceSize=128m", \
    "-Xss256k", \
    "-XX:+ExitOnOutOfMemoryError", \
    "-Dspring.profiles.active=prod", \
    "-jar", "app.jar"]
