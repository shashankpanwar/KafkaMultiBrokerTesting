#!/usr/bin/env bash
set -euo pipefail

# default values (can be overridden by env) | (safe fallbacks)
: "${NODE_ID:=1}"
: "${PROCESS_ROLES:=broker,controller}"   # can be "broker" or "controller,broker"
: "${BROKER_ID:=${NODE_ID}}"
: "${KRAFT_CONTROLLER_LISTENER:=CONTROLLER://0.0.0.0:9093}"
: "${PLAINTEXT_LISTENER:=PLAINTEXT://0.0.0.0:9092}"
: "${ADVERTISED_LISTENERS:=PLAINTEXT://localhost:9092}"
: "${CONTROLLER_QUORUM_VOTERS:=1@controller1:9093,2@controller2:9093,3@controller3:9093}"

# IMPORTANT: set metadata dir default here to avoid "unbound variable" error
: "${LOG_DIR:=/var/lib/kafka/data}"          # where topic logs live
: "${METADATA_DIR:=/var/lib/kafka/metadata}" # must NOT be a subdir of LOG_DIR
: "${KRAFT_CLUSTER_ID:=}"                  # optional; if empty we'll generate one

# create data dirs
mkdir -p "${LOG_DIR}" "${METADATA_DIR}" /var/log/kafka /opt/config

# Create server.properties from template by simple env substitution
TEMPLATE=/opt/scripts/server.properties.template
CONFIG=/opt/config/server.properties
mkdir -p /opt/config

envsubst < "${TEMPLATE}" > "${CONFIG}"

echo "==== Start kafka with config ===="
cat "${CONFIG}"
echo "================================="


# If metadata does not have meta.properties, format the storage
META_FILE="${METADATA_DIR}/meta.properties"
if [ ! -f "${META_FILE}" ]; then
  echo "No meta.properties found at ${META_FILE} — initializing metadata storage..."

  # If user supplied KRAFT_CLUSTER_ID use it, otherwise generate a uuid
  if [ -z "${KRAFT_CLUSTER_ID}" ]; then
    echo "KRAFT_CLUSTER_ID not provided — generating one..."
    KRAFT_CLUSTER_ID=$(/opt/bin/kafka-storage.sh random-uuid)
    echo "Generated cluster id: ${KRAFT_CLUSTER_ID}"
  else
    echo "Using provided KRAFT_CLUSTER_ID=${KRAFT_CLUSTER_ID}"
  fi

  # Format the storage for this node (idempotent for fresh dirs)
  /opt/bin/kafka-storage.sh format -t "${KRAFT_CLUSTER_ID}" -c "${CONFIG}"
  echo "Formatting completed for node ${NODE_ID}."
else
  echo "meta.properties found — skipping format."
fi

# Start Kafka server (KRaft capable) - uses server.properties
exec /opt/bin/kafka-server-start.sh "${CONFIG}"