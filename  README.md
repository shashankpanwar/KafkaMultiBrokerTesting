# Kafka Multi Broker Testing (KRaft Mode)

This project demonstrates a **3-broker Kafka KRaft cluster** running in Docker and tested using a **Spring Boot** application with multiple producers and consumers.

## 🏗 Project Structure

build-script-kafka/
├── Dockerfile
├── docker-compose.yml
├── entrypoint.sh
├── server.properties.template
src/
├── main/java/...
└── test/java/...


## 🚀 How to Run

```bash
cd build-script-kafka
docker compose up -d

./gradlew bootRun

🧩 Topics

topic-one

topic-two

topic-dlq

🧪 Features

Multi-broker KRaft mode (no Zookeeper)

Producers with KafkaTemplate

Consumers with retry & DLQ handling

Integration tests verifying producer/consumer flow

