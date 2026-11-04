FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY spring-context-compactor-starter/pom.xml spring-context-compactor-starter/pom.xml
COPY trade-clearing-compactor-demo/pom.xml trade-clearing-compactor-demo/pom.xml

RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY spring-context-compactor-starter/src spring-context-compactor-starter/src
COPY trade-clearing-compactor-demo/src trade-clearing-compactor-demo/src

RUN ./mvnw -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=builder /workspace/trade-clearing-compactor-demo/target/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
