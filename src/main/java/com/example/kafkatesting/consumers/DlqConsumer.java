package com.example.kafkatesting.consumers;

import com.example.kafkatesting.constants.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DlqConsumer {

    @KafkaListener(topics = KafkaTopics.TOPIC_DLQ, groupId = KafkaTopics.TOPIC_DLQ_GROUP)
    public void handleDlq(String payload) {
        System.err.println("DLQ received: " + payload);
        // alerting / store for manual inspection / push to error DB
    }
}