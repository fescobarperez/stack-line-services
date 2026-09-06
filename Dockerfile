# ── Compilación ───────────────────────────────────────────────────────
# Las dependencias se resuelven en una capa aparte del código: cambiar una
# clase no vuelve a bajar medio Maven Central en cada despliegue.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

COPY gradlew gradle.properties settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src src
RUN ./gradlew --no-daemon shadowJar --quiet

# ── Ejecución ─────────────────────────────────────────────────────────
# JRE y no JDK: la imagen final no necesita compilador y pesa la mitad.
FROM eclipse-temurin:25-jre
WORKDIR /app

# Usuario sin privilegios: si alguien logra ejecutar algo dentro del
# contenedor, que no sea root.
RUN useradd --system --uid 10001 stackline
USER stackline

COPY --from=build --chown=stackline /src/build/libs/*-all.jar app.jar

EXPOSE 8080
# Sin -Xmx a propósito: la JVM lee el límite del cgroup del contenedor y toma
# el 25% por defecto. Ajustar con JAVA_OPTS si el servidor es chico.
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
