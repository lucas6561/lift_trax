FROM eclipse-temurin:21-jdk-jammy AS build

RUN apt-get update \
    && apt-get install --yes --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon qualityGate installDist

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /app/build/install/lift-trax-java /app/lift_trax
COPY config/lifttrax-render.properties /app/config/lifttrax-hosted.properties

ENV PORT=10000
EXPOSE 10000

CMD ["sh", "-c", "exec java -Dlifttrax.config=/app/config/lifttrax-hosted.properties -cp '/app/lift_trax/lib/*' com.lifttrax.cli.WebServerCli \"$PORT\""]
