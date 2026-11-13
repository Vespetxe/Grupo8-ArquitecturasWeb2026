# Build context: TicketYa/inventarioReserva_service
FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml ./
COPY src/main/ ./src/main/

RUN mvn --batch-mode --no-transfer-progress -Dmaven.test.skip=true package

FROM eclipse-temurin:21.0.11_10-jre-jammy AS runtime

RUN groupadd --gid 10001 ticketya \
    && useradd --uid 10001 --gid ticketya --no-create-home \
        --shell /usr/sbin/nologin ticketya

WORKDIR /app

COPY --from=build --chown=10001:10001 /build/target/inventarioReservas_service-0.0.1-SNAPSHOT.jar /app/app.jar

USER 10001:10001

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
