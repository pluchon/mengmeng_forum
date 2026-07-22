# 多阶段构建：Maven 打包 + JRE 运行
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# 构建阶段通过宿主机代理直连 Maven Central。
ARG MAVEN_PROXY_HOST=host.docker.internal
ARG MAVEN_PROXY_PORT=7897
ENV http_proxy=http://${MAVEN_PROXY_HOST}:${MAVEN_PROXY_PORT}
ENV https_proxy=http://${MAVEN_PROXY_HOST}:${MAVEN_PROXY_PORT}
ENV HTTP_PROXY=http://${MAVEN_PROXY_HOST}:${MAVEN_PROXY_PORT}
ENV HTTPS_PROXY=http://${MAVEN_PROXY_HOST}:${MAVEN_PROXY_PORT}
ENV NO_PROXY=localhost,127.0.0.1
ENV MAVEN_OPTS="-Dhttp.proxyHost=${MAVEN_PROXY_HOST} -Dhttp.proxyPort=${MAVEN_PROXY_PORT} -Dhttps.proxyHost=${MAVEN_PROXY_HOST} -Dhttps.proxyPort=${MAVEN_PROXY_PORT} -Dhttp.nonProxyHosts=localhost|127.*|[::1]"

COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN apk add --no-cache wget \
    && adduser -D -u 1000 appuser
COPY --from=builder /build/target/backend-*.jar app.jar
USER appuser
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
