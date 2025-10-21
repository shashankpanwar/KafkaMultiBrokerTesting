package com.example.kafkatesting;

import com.example.kafkatesting.constants.KafkaTopics;
import com.example.kafkatesting.consumers.ConsumerOne;
import com.example.kafkatesting.consumers.ConsumerOneCopy;
import com.example.kafkatesting.consumers.ConsumerTwo;
import com.example.kafkatesting.producers.ProducerOneService;
import com.example.kafkatesting.producers.ProducerTwoService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Duration;

@SpringBootTest
@EmbeddedKafka(partitions = 3, topics = {KafkaTopics.TOPIC_ONE,KafkaTopics.TOPIC_TWO}, brokerProperties = {"listeners=PLAINTEXT://localhost:9092", "port=9092"})
class KafkaMultiTestingApplicationTests {

    @Autowired
    private ProducerOneService producerOne;

    @Autowired
    private ProducerTwoService producerTwo;

    @Autowired
    private ConsumerOne consumerOne;

    @Autowired
    private ConsumerOneCopy consumerOneCopy;

    @Autowired
    private ConsumerTwo consumerTwo;

    @Test
    void testMultipleProducersAndConsumers() {
        // send 5 messages to topic-one
        for (int i = 0; i < 5; i++) {
            producerOne.send("k" + i, "p1-msg-" + i);
        }

        // send 3 messages to topic-two (async)
        for (int i = 0; i < 3; i++) {
            producerTwo.sendAsync("tk" + i, "p2-msg-" + i);
        }

        // wait for consumerOne to see at least 5 messages and consumerTwo to see >=3
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(consumerOne.getCounter() >= 5)
        );

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                org.junit.jupiter.api.Assertions.assertTrue(consumerTwo.getProcessedCount() >= 3)
        );
    }
}
