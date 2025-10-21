package com.example.kafkatesting.consumers;

import com.example.kafkatesting.constants.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConsumerOne {
    private final AtomicInteger counter = new AtomicInteger(0);

    @KafkaListener(topics = KafkaTopics.TOPIC_ONE, groupId = KafkaTopics.TOPIC_GROUP_ONE, clientIdPrefix = KafkaTopics.TOPIC_ONE_PREFIX)
    public void consume(String message) {
        int c = counter.incrementAndGet();
        System.out.println("ConsumerOne received: " + message + " (count=" + c + ")");
    }

    public int getCounter() {
        return counter.get();
    }
}
