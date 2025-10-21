# Use a minimal JDK base (adjust as needed)
FROM openjdk:17-jdk-slim

ARG KAFKA_VERSION=3.9.1
ARG KAFKA_SCALA=2.13
ARG KAFKA_TGZ="kafka_${KAFKA_SCALA}-${KAFKA_VERSION}.tgz"
ARG KAFKA_DOWNLOAD_URL="https://downloads.apache.org/kafka/${KAFKA_VERSION}/${KAFKA_TGZ}"

ENV KAFKA_HOME=/opt/kafka
ENV PATH="${KAFKA_HOME}/bin:${PATH}"

# Create runtime dirs
RUN mkdir -p /opt && mkdir -p /var/lib/kafka/data /var/log/kafka /opt/scripts

# Download Kafka binary (from Apache official download site)
# Note: You may want to pin to a mirror or checksum in production.
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates gettext-base tar && \
    curl -fsSL "${KAFKA_DOWNLOAD_URL}" -o /tmp/${KAFKA_TGZ} && \
    tar -xzf /tmp/${KAFKA_TGZ} -C /opt --strip-components=1 && \
    rm -f /tmp/${KAFKA_TGZ} && \
    apt-get remove -y --purge curl && apt-get autoremove -y && rm -rf /var/lib/apt/lists/*

# Copy entrypoint and template files
COPY entrypoint.sh /opt/scripts/entrypoint.sh
COPY server.properties.template /opt/scripts/server.properties.template
RUN chmod +x /opt/scripts/entrypoint.sh

VOLUME ["/var/lib/kafka/data", "/var/log/kafka"]

EXPOSE 9092 9093 9094 9098

WORKDIR /opt

ENTRYPOINT ["/opt/scripts/entrypoint.sh"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
  CMD /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 >/dev/null 2>&1 || exit 1
