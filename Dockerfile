# ---- build ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

# ---- run ----
FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 app && mkdir -p /data && chown app /data
USER app
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
ENV WHEREISMYMONEYGOING_LOCAL_RULES_PATH=/data/merchant.local.yml
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]