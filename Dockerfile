# syntax=docker/dockerfile:1.4

# =============================================================
# Stage 1: Build Application JAR & Extract Layers
# =============================================================
FROM eclipse-temurin:21-jdk-noble AS builder
WORKDIR /workspace

# Copy Gradle wrapper and configuration files first to optimize Docker layer caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

# Copy application sources and compile production executable
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Extract the launcher and layered components using Spring Boot tools
WORKDIR /workspace/extracted
RUN java -Djarmode=tools -jar /workspace/build/libs/stock-ledger-0.0.1-SNAPSHOT.jar extract --launcher --destination .

# =============================================================
# Stage 2: Create Stripped Custom JRE via jlink (Glibc-compatible)
# =============================================================
FROM eclipse-temurin:21-jdk-noble AS jre-builder

RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.logging,java.naming,java.desktop,java.management,java.security.jgss,java.instrument,java.sql,java.xml,java.net.http,java.compiler,java.rmi,jdk.unsupported \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=2 \
    --output /custom-jre

# =============================================================
# Stage 3: Zero-CVE Production Runtime on Google Distroless (Debian glibc)
# =============================================================
FROM gcr.io/distroless/java-base-debian12:nonroot AS runtime
WORKDIR /app

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV SPRING_PROFILES_ACTIVE=prod

# Copy custom JRE (matching glibc)
COPY --from=jre-builder --chown=nonroot:nonroot /custom-jre /opt/java/openjdk

# Copy Spring Boot exploded layers in order of least to most frequently modified
COPY --from=builder --chown=nonroot:nonroot /workspace/extracted/BOOT-INF/lib /app/BOOT-INF/lib
COPY --from=builder --chown=nonroot:nonroot /workspace/extracted/META-INF /app/META-INF
COPY --from=builder --chown=nonroot:nonroot /workspace/extracted/org /app/org
COPY --from=builder --chown=nonroot:nonroot /workspace/extracted/BOOT-INF/classes /app/BOOT-INF/classes

USER nonroot:nonroot

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseG1GC", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "org.springframework.boot.loader.launch.JarLauncher"]
