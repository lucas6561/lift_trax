FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon qualityGate installDist

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=build /app/build/install/lift_trax /app/lift_trax
COPY config/lifttrax-hosted.example.properties /app/config/lifttrax-hosted.properties

ENV PORT=10000
EXPOSE 10000

CMD ["sh", "-c", "exec java -Dlifttrax.config=/app/config/lifttrax-hosted.properties -cp '/app/lift_trax/lib/*' com.lifttrax.cli.WebServerCli data/lifts.db \"$PORT\""]
