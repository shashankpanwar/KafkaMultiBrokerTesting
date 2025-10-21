#!/usr/bin/env bash
set -euo pipefail

# ------------ defaults (override with env) ------------
: "${NODE_ID:=1}"
: "${PROCESS_ROLES:=broker,controller}"
: "${BROKER_ID:=${NODE_ID}}"

# Two configuration styles supported:
# 1) Modern multi-listener style (preferred)
#    LISTENERS, LISTENER_SECURITY_PROTOCOL_MAP, ADVERTISED_LISTENERS,
#    INTER_BROKER_LISTENER_NAME, CONTROLLER_LISTENER_NAME
#
# 2) Backwards compatible (simple) style
#    PLAINTEXT_LISTENER, KRAFT_CONTROLLER_LISTENER, ADVERTISED_LISTENERS (simple)
#
# If LISTENERS is provided we prefer it; otherwise we build from PLAINTEXT/KRAFT_CONTROLLER.

: "${LISTENERS:=}"                              # e.g. INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093, user may provide multi-listener string
: "${LISTENER_SECURITY_PROTOCOL_MAP:=}"         # e.g. INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:CONTROLLER
: "${ADVERTISED_LISTENERS:=}"                   # e.g. INTERNAL://kafka1:9092,EXTERNAL://localhost:9092,CONTROLLER://kafka1:9093
: "${INTER_BROKER_LISTENER_NAME:=INTERNAL}"     # default internal name
: "${CONTROLLER_LISTENER_NAME:=CONTROLLER}"

# Backwards-compatible names (if user set them)
: "${PLAINTEXT_LISTENER:=PLAINTEXT://0.0.0.0:9092}"    # legacy
: "${KRAFT_CONTROLLER_LISTENER:=CONTROLLER://0.0.0.0:9093}"

# Controller quorum voters (must be the same on all nodes, and use container hostnames for intra-cluster comms)
: "${CONTROLLER_QUORUM_VOTERS:=1@kafka1:9093,2@kafka2:9093,3@kafka3:9093}"

# Data dirs & cluster id
: "${LOG_DIR:=/var/lib/kafka/data}"
: "${METADATA_DIR:=/var/lib/kafka/metadata}"
: "${KRAFT_CLUSTER_ID:=}"

# Convenience: ensure /opt/config dir exists (we write effective server.properties there)
CONFIG_DIR=/opt/config
TEMPLATE=/opt/scripts/server.properties.template
CONFIG=${CONFIG_DIR}/server.properties

# create runtime dirs
mkdir -p "${LOG_DIR}" "${METADATA_DIR}" /var/log/kafka "${CONFIG_DIR}"

# If user didn't provide LISTENERS/ADVERTISED_LISTENERS, derive them from legacy vars
# If LISTENERS is provided we prefer it; otherwise we build from legacy vars.
: "${LISTENER_SECURITY_PROTOCOL_MAP:=}"         # required to map listener names to protocols

# If no LISTENERS provided, construct a sane default (INTERNAL/EXTERNAL/CONTROLLER).
if [ -z "${LISTENERS}" ]; then
  export LISTENERS="INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093"
fi

# If no mapping provided, apply a safe default mapping that works for dev setups.
if [ -z "${LISTENER_SECURITY_PROTOCOL_MAP}" ]; then
  export LISTENER_SECURITY_PROTOCOL_MAP="INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:PLAINTEXT"
fi


# If ADVERTISED_LISTENERS not provided, attempt to assemble a reasonable default using NODE_ID
if [ -z "${ADVERTISED_LISTENERS}" ]; then
  # Default mapping: INTERNAL->kafkaN:9092, EXTERNAL->localhost:<host-port mapped by compose>
  # Because we don't know host port from inside container, user should set ADVERTISED_LISTENERS in docker-compose.
  # Provide a fallback to kafkaN for INTERNAL and to localhost:9092 for EXTERNAL (common dev case).
  export ADVERTISED_LISTENERS="INTERNAL://kafka${NODE_ID}:9092,EXTERNAL://localhost:9092,CONTROLLER://kafka${NODE_ID}:9093"
fi

# Export controller quorum voters env for template substitution
export CONTROLLER_QUORUM_VOTERS

# Show what will be used
echo "==== Entrypoint config env ===="
echo " NODE_ID=${NODE_ID}"
echo " BROKER_ID=${BROKER_ID}"
echo " PROCESS_ROLES=${PROCESS_ROLES}"
echo " LISTENERS=${LISTENERS}"
echo " LISTENER_SECURITY_PROTOCOL_MAP=${LISTENER_SECURITY_PROTOCOL_MAP}"
echo " ADVERTISED_LISTENERS=${ADVERTISED_LISTENERS}"
echo " INTER_BROKER_LISTENER_NAME=${INTER_BROKER_LISTENER_NAME}"
echo " CONTROLLER_LISTENER_NAME=${CONTROLLER_LISTENER_NAME}"
echo " CONTROLLER_QUORUM_VOTERS=${CONTROLLER_QUORUM_VOTERS}"
echo " LOG_DIR=${LOG_DIR}"
echo " METADATA_DIR=${METADATA_DIR}"
echo " KRAFT_CLUSTER_ID=${KRAFT_CLUSTER_ID:-<auto>}"
echo "================================"

# Render server.properties template using envsubst
if [ ! -f "${TEMPLATE}" ]; then
  echo "ERROR: server.properties.template not found at ${TEMPLATE}"
  exit 1
fi

# envsubst is required (gettext-base). Ensure image installs it.
envsubst < "${TEMPLATE}" > "${CONFIG}"

echo "==== Effective server.properties ===="
cat "${CONFIG}"
echo "===================================="

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
  echo "Running: /opt/bin/kafka-storage.sh format -t \"${KRAFT_CLUSTER_ID}\" -c \"${CONFIG}\""
  /opt/bin/kafka-storage.sh format -t "${KRAFT_CLUSTER_ID}" -c "${CONFIG}"
  echo "Formatting completed for node ${NODE_ID}."
else
  echo "meta.properties found — skipping format."
fi

# Start Kafka server (KRaft capable) - uses server.properties
exec /opt/bin/kafka-server-start.sh "${CONFIG}"