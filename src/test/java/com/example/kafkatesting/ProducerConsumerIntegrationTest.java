package com.example.kafkatesting;

import com.example.kafkatesting.constants.KafkaTopics;
import com.example.kafkatesting.consumers.ConsumerOne;
import com.example.kafkatesting.consumers.ConsumerTwo;
import com.example.kafkatesting.producers.ProducerOneService;
import com.example.kafkatesting.producers.ProducerTwoService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ProducerConsumerIntegrationTest {

    @Autowired
    ProducerOneService producerOne;

    @Autowired
    ProducerTwoService producerTwo;

    @Autowired
    ConsumerOne consumerOne;

    @Autowired
    ConsumerTwo consumerTwo; // for checking processed count etc

    @Autowired
    KafkaTemplate<String, String> kafkaTemplate; // optional helper

    private KafkaConsumer<String, String> dlqConsumer;

    @BeforeEach
    void setUp() {
        // configure a simple KafkaConsumer to read DLQ (host bootstrap servers from test profile)
        Properties props = new Properties();
        props.put("bootstrap.servers", "localhost:9092,localhost:9094,localhost:9096");
        props.put("group.id", "test-dlq-reader-" + System.currentTimeMillis());
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("auto.offset.reset", "earliest");
        dlqConsumer = new KafkaConsumer<>(props);
        dlqConsumer.subscribe(Collections.singletonList(KafkaTopics.TOPIC_DLQ));
    }

    @AfterEach
    void tearDown() {
        if (dlqConsumer != null) dlqConsumer.close();
    }

    @Test
    @Order(1)
    void testProducerOneAndConsumerOne_processing() {
        // send a message
        String key = "p1-key-" + System.currentTimeMillis();
        String payload = "hello from test p1";
        producerOne.send(key, payload);

        // Wait for consumer to process. If you have a consumer bean with a processed counter,
        // use that. Here we rely on side-effect: consumed by app logs / processed count.
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .pollInterval(Duration.ofMillis(200))
                .untilAsserted(() -> {
                    // implement appropriate verification:
                    // - if ConsumerOne has a counter you can autowire, assert its value increased
                    // - OR, attempt to consume from the topic to ensure message exists (if consumer commits later)
                    // Example: try to find the message in topic using KafkaConsumer (not ideal with auto commit).
                    // For brevity we assume consumer processed successfully if no exception thrown earlier.
                    System.out.println("Counter: "+consumerOne.getCounter());
                    Assertions.assertTrue(consumerOne.getCounter() >= 1);
                });
    }

    @Test
    @Order(2)
    void testProducerTwo_retryAndDlqFlow() {
        // Produce payload that will cause consumer to throw and trigger retry (consumerTwo simulates fail for "fail")
        String key = "retry-key-" + System.currentTimeMillis();
        String payload = "please-fail"; // make sure ConsumerTwo logic treats containing "fail" as failure

        producerTwo.sendAsync(key, payload);

        // Wait for processing attempts and final DLQ send
        Awaitility.await()
                .atMost(30, TimeUnit.SECONDS)
                .pollInterval(Duration.ofSeconds(1))
                .untilAsserted(() -> {
                    // Poll DLQ to see final message
                    ConsumerRecords<String, String> recs = dlqConsumer.poll(Duration.ofSeconds(1));
                    boolean found = false;
                    for (ConsumerRecord<String, String> r : recs) {
                        System.out.println("Value: "+r.value()+", key: "+r.key());
                        if (key.equals(r.key()) && r.value().equals(payload)) {
                            found = true;
                            break;
                        }
                    }
                    Assertions.assertTrue(found, "Expected message on DLQ after retries");
                });
    }
}
