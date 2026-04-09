FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests clean package

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV LANG=C.UTF-8
COPY --from=build /workspace/target/hospital-appointment-system-*.jar /app/app.jar
EXPOSE 8000
ENTRYPOINT ["java","-jar","/app/app.jar"]
