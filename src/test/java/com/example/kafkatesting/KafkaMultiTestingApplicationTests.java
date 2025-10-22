package com.example.kafkatesting;

import com.example.kafkatesting.constants.KafkaTopics;
import com.example.kafkatesting.consumers.ConsumerOne;
import com.example.kafkatesting.consumers.ConsumerOneCopy;
import com.example.kafkatesting.consumers.ConsumerTwo;
import com.example.kafkatesting.producers.ProducerOneService;
import com.example.kafkatesting.producers.ProducerTwoService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private KafkaAdmin kafkaAdmin; // used to create topics programmatically if needed

    private AdminClient adminClient;

    @BeforeAll
    void beforeAll() {
        // create an AdminClient from KafkaAdmin to create topics if brokers don't auto-create
        Properties props = new Properties();
        props.putAll(kafkaAdmin.getConfigurationProperties());
        adminClient = AdminClient.create(props);

        // Ensure topics exist (3 partitions). If already exist, creation is ignored.
        List<NewTopic> topics = Arrays.asList(
                new NewTopic(KafkaTopics.TOPIC_ONE, 3, (short) 3),
                new NewTopic(KafkaTopics.TOPIC_TWO, 3, (short) 3),
                new NewTopic(KafkaTopics.TOPIC_DLQ, 1, (short) 1)
        );

        try {
            adminClient.createTopics(topics).all().get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // creation may fail if topics already exist — that's okay
            System.out.println("Topic creation: " + e.getMessage());
        }
    }

    @AfterAll
    void afterAll() {
        if (adminClient != null) adminClient.close();
    }

    @BeforeEach
    void beforeEach() {
        // If your consumer beans maintain counters, reset them for deterministic tests.
        // Add a reset method in your consumer beans (recommended) and call it here:
        try {
            consumerOne.getClass().getMethod("resetCounter").invoke(consumerOne);
        } catch (NoSuchMethodException ignored) {
            // not provided — fine, we'll rely on Awaitility and unique keys
        } catch (Exception e) {
            System.err.println("Unable to reset consumerOne counter: " + e.getMessage());
        }

        try {
            consumerTwo.getClass().getMethod("resetProcessedCount").invoke(consumerTwo);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            System.err.println("Unable to reset consumerTwo counter: " + e.getMessage());
        }
    }

    @Test
    void testMultipleProducersAndConsumers() {
        // produce 5 messages to topic-one (synchronous wait for send completion)
        List<CompletableFuture<?>> futures1 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String key = "k" + i + "-" + System.currentTimeMillis();
            String payload = "p1-msg-" + i;
            CompletableFuture<?> fut = producerOne.send(key, payload);
            if (fut != null) futures1.add(fut);
        }

        // wait for all producerOne sends to complete (timeout)
        CompletableFuture<Void> combined1 = CompletableFuture.allOf(
                futures1.toArray(new CompletableFuture[0])
        );
        try {
            combined1.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new RuntimeException(e);
        }

        // produce 3 messages to topic-two (async). Wait for sends to finish if futures are returned.
        List<CompletableFuture<?>> futures2 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String key = "tk" + i + "-" + System.currentTimeMillis();
            String payload = "p2-msg-" + i;
            CompletableFuture<?> fut = producerTwo.sendAsync(key, payload);
            if (fut != null) futures2.add(fut);
        }

        if (!futures2.isEmpty()) {
            CompletableFuture<Void> combined2 = CompletableFuture.allOf(
                    futures2.toArray(new CompletableFuture[0])
            );
            try {
                combined2.get(10, TimeUnit.SECONDS);
            } catch (InterruptedException | TimeoutException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        } else if (kafkaTemplate != null) {
            // fallback flush to ensure sends were pushed out
            kafkaTemplate.flush();
        }


        // wait for consumerOne to see at least 5 messages and consumerTwo to see >=3
        Awaitility.await().atMost(Duration.ofSeconds(15)).pollInterval(Duration.ofMillis(200)).untilAsserted(() ->
        {
            System.out.println("ConsumerOne - Counter: " + consumerOne.getCounter());
            org.junit.jupiter.api.Assertions.assertTrue(consumerOne.getCounter() >= 5);
        });

        // Wait until consumerTwo has processed at least 3 messages (including retry logic if any)
        Awaitility.await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
        {
            System.out.println("ConsumerTwo - Counter: " + consumerTwo.getProcessedCount());
            org.junit.jupiter.api.Assertions.assertTrue(consumerTwo.getProcessedCount() >= 3);
        });
    }
}
