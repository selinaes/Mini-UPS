
# Build stage
FROM gradle:7.3.3-jdk17 AS build
COPY . /home/gradle/src
WORKDIR /home/gradle/src
RUN gradle build -x test --no-daemon

# Run stage
FROM openjdk:17-jdk-slim
RUN mkdir /app
COPY --from=build /home/gradle/src/build/libs/*.jar /app/
RUN #gradlew run
#ENTRYPOINT ["java", "-jar", "/app/your-project-name.jar"]
ENTRYPOINT ["java","-XX:+UseContainerSupport", "-Djava.security.egd=file:/dev/./urandom","-jar","/app/ups-application-1.0-shadow.jar-1.0-SNAPSHOT-all.jar"]
