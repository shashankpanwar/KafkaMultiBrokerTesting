#!/usr/bin/env bash

echo "Starting creating metadata"

CLUSTER_ID=$(docker run --rm kafka-kraft:3.9.1 /opt/bin/kafka-storage.sh random-uuid)
echo $CLUSTER_ID

docker run --rm \
  -e NODE_ID=1 -e BROKER_ID=1 -e PROCESS_ROLES="broker,controller" \
  -e LISTENERS="INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093" \
  -e LISTENER_SECURITY_PROTOCOL_MAP="INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:CONTROLLER" \
  -e ADVERTISED_LISTENERS="INTERNAL://kafka1:9092,EXTERNAL://localhost:9092,CONTROLLER://kafka1:9093" \
  -e INTER_BROKER_LISTENER_NAME=INTERNAL \
  -e CONTROLLER_LISTENER_NAME=CONTROLLER \
  -e CONTROLLER_QUORUM_VOTERS="1@kafka1:9093,2@kafka2:9093,3@kafka3:9093" \
  -e KRAFT_CLUSTER_ID="${CLUSTER_ID}" \
  -v $(pwd)/data/kafka1/data:/var/lib/kafka/data \
  -v $(pwd)/data/kafka1/metadata:/var/lib/kafka/metadata \
  --entrypoint /bin/bash kafka-kraft:3.9.1 -c "\
    mkdir -p /opt/config && \
    envsubst < /opt/scripts/server.properties.template > /opt/config/server.properties && \
    /opt/bin/kafka-storage.sh format -t ${CLUSTER_ID} -c /opt/config/server.properties && \
    echo 'formatted kafka1'"

sleep 300

 docker run --rm \
   -e NODE_ID=2 -e BROKER_ID=2 -e PROCESS_ROLES="broker,controller" \
   -e LISTENERS="INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093" \
   -e LISTENER_SECURITY_PROTOCOL_MAP="INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:CONTROLLER" \
   -e ADVERTISED_LISTENERS="INTERNAL://kafka2:9092,EXTERNAL://localhost:9094,CONTROLLER://kafka2:9093" \
   -e INTER_BROKER_LISTENER_NAME=INTERNAL \
   -e CONTROLLER_LISTENER_NAME=CONTROLLER \
   -e CONTROLLER_QUORUM_VOTERS="1@kafka1:9093,2@kafka2:9093,3@kafka3:9093" \
   -e KRAFT_CLUSTER_ID="${CLUSTER_ID}" \
   -v $(pwd)/data/kafka1/data:/var/lib/kafka/data \
   -v $(pwd)/data/kafka1/metadata:/var/lib/kafka/metadata \
   --entrypoint /bin/bash kafka-kraft:3.9.1 -c "\
     mkdir -p /opt/config && \
     envsubst < /opt/scripts/server.properties.template > /opt/config/server.properties && \
     /opt/bin/kafka-storage.sh format -t ${CLUSTER_ID} -c /opt/config/server.properties && \
     echo 'formatted kafka2'"

     sleep 300

 docker run --rm \
   -e NODE_ID=3 -e BROKER_ID=3 -e PROCESS_ROLES="broker,controller" \
   -e LISTENERS="INTERNAL://0.0.0.0:9092,EXTERNAL://0.0.0.0:29092,CONTROLLER://0.0.0.0:9093" \
   -e LISTENER_SECURITY_PROTOCOL_MAP="INTERNAL:PLAINTEXT,EXTERNAL:PLAINTEXT,CONTROLLER:CONTROLLER" \
   -e ADVERTISED_LISTENERS="INTERNAL://kafka3:9092,EXTERNAL://localhost:9096,CONTROLLER://kafka3:9093" \
   -e INTER_BROKER_LISTENER_NAME=INTERNAL \
   -e CONTROLLER_LISTENER_NAME=CONTROLLER \
   -e CONTROLLER_QUORUM_VOTERS="1@kafka1:9093,2@kafka2:9093,3@kafka3:9093" \
   -e KRAFT_CLUSTER_ID="${CLUSTER_ID}" \
   -v $(pwd)/data/kafka1/data:/var/lib/kafka/data \
   -v $(pwd)/data/kafka1/metadata:/var/lib/kafka/metadata \
   --entrypoint /bin/bash kafka-kraft:3.9.1 -c "\
     mkdir -p /opt/config && \
     envsubst < /opt/scripts/server.properties.template > /opt/config/server.properties && \
     /opt/bin/kafka-storage.sh format -t ${CLUSTER_ID} -c /opt/config/server.properties && \
     echo 'formatted kafka3'"
