package com.example.kafkatesting.producers;

import com.example.kafkatesting.constants.KafkaTopics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class ProducerTwoService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public ProducerTwoService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> sendAsync(String key, String payload) {
        return kafkaTemplate.send(KafkaTopics.TOPIC_TWO, key, payload);
    }

    public void sendSync(String key, String payload) throws Exception {
        kafkaTemplate.send(KafkaTopics.TOPIC_TWO, key, payload).get(); // blocking
    }
}
