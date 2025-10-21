package com.example.kafkatesting.producers;

import com.example.kafkatesting.constants.KafkaTopics;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ProducerOneService {
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public ProducerOneService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> send(String key, String payload) {
        CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(KafkaTopics.TOPIC_ONE, key, payload);

        // Add callback/side-effect logging
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                RecordMetadata meta = result.getRecordMetadata();
                System.out.printf("✅ Sent message key=%s to topic=%s partition=%d offset=%d%n",
                        key, meta.topic(), meta.partition(), meta.offset());
            } else {
                System.err.printf("❌ Failed to send message key=%s due to %s%n", key, ex.getMessage());
            }
        });
        return future;
    }
}
