# syntax=docker/dockerfile:1.28
FROM cgr.dev/chainguard/jre:latest
WORKDIR /app
ARG BUILD_DATE
ARG BUILD_VERSION=dev
ARG BUILD_REVISION
LABEL org.opencontainers.image.title="AdapterFS" \
      org.opencontainers.image.description="Filesystem gateway for Kubernetes" \
      org.opencontainers.image.source="https://github.com/wenisch-tech/AdapterFS" \
      org.opencontainers.image.licenses="AGPL-3.0" \
      org.opencontainers.image.version="${BUILD_VERSION}" \
      org.opencontainers.image.revision="${BUILD_REVISION}" \
      org.opencontainers.image.created="${BUILD_DATE}"
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/urandom"
COPY --chown=65532:65532 target/adapterfs.jar /app/adapterfs.jar
COPY --chown=65532:65532 docker/state/ /var/lib/adapterfs/
EXPOSE 8080 9000 2222 2121 30000-30009
ENTRYPOINT ["java","-jar","/app/adapterfs.jar"]
