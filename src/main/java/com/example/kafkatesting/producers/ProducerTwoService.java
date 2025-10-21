package com.example.kafkatesting.producers;

import com.example.kafkatesting.constants.KafkaTopics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProducerTwoService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    public ProducerTwoService(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendAsync(String key, String payload) {
        kafkaTemplate.send(KafkaTopics.TOPIC_TWO, key, payload);
    }

    public void sendSync(String key, String payload) throws Exception {
        kafkaTemplate.send(KafkaTopics.TOPIC_TWO, key, payload).get(); // blocking
    }
}
