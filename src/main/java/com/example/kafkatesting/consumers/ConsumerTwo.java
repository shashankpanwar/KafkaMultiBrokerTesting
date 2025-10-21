package com.example.kafkatesting.consumers;

import com.example.kafkatesting.constants.KafkaTopics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ConsumerTwo {

    private static final String RETRY_HEADER = "x-retry-count";
    private static final int MAX_RETRIES = 3;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AtomicInteger processed = new AtomicInteger(0);

    public ConsumerTwo(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Manual-ack listener for topic-two.
     * Uses manual acknowledgment to commit offsets only after successful processing.
     */
    @KafkaListener(topics = KafkaTopics.TOPIC_TWO, groupId = KafkaTopics.TOPIC_GROUP_TWO, containerFactory = "manualAckListenerFactory", clientIdPrefix = KafkaTopics.TOPIC_TWO_PREFIX)
    public void listenManual(ConsumerRecord<String, String> record, Acknowledgment ack, @Payload String payload) {
        String key = record.key();
        System.out.printf("ConsumerTwo received key=%s payload=%s%n", key, payload);

        try {
            // Process message (place your business logic here)
            processMessage(key, payload);

            // On success, acknowledge the record
            ack.acknowledge();
            int count = processed.incrementAndGet();
            System.out.printf("ConsumerTwo processed successfully (count=%d) key=%s%n", count, key);

        } catch (Exception ex) {
            System.err.printf("ConsumerTwo processing failed for key=%s: %s%n", key, ex.getMessage());
            // Try retry logic
            int retries = extractRetryCount(record.headers()).orElse(0);
            if (retries < MAX_RETRIES) {
                int nextRetry = retries + 1;
                System.out.printf("Re-publishing message key=%s to %s with retry=%d%n", key, KafkaTopics.TOPIC_TWO, nextRetry);

                // Build headers for republished message
                RecordHeaders newHeaders = new RecordHeaders();
                // copy existing headers except retry header if you want (optional)
                Headers existing = record.headers();
                existing.forEach(h -> {
                    if (!RETRY_HEADER.equals(h.key())) {
                        newHeaders.add(h);
                    }
                });
                // add/update retry header
                newHeaders.add(new RecordHeader(RETRY_HEADER, Integer.toString(nextRetry).getBytes(StandardCharsets.UTF_8)));

                // Create ProducerRecord with headers and send
                ProducerRecord<String, String> pr = new ProducerRecord<>(
                        KafkaTopics.TOPIC_TWO,
                        null,               // partition (let broker decide)
                        record.key(),
                        payload,
                        newHeaders
                );

                kafkaTemplate.send(pr).whenComplete((res, ex2) -> {
                    if (ex2 != null) {
                        System.err.printf("Failed to republish for retry key=%s: %s%n", key, ex2.getMessage());
                    } else {
                        System.out.printf("Republished key=%s for retry=%d%n", key, nextRetry);
                    }
                });

                // Acknowledge current offset so we don't reprocess this exact record
                ack.acknowledge();
            } else {
                // Send to DLQ (dead-letter queue)
                System.err.printf("Max retries reached for key=%s. Sending to DLQ %s%n", key, KafkaTopics.TOPIC_DLQ);
                kafkaTemplate.send(KafkaTopics.TOPIC_DLQ, record.key(), payload)
                        .exceptionally(e -> {
                            System.err.printf("Failed to send to DLQ for key=%s: %s%n", key, e.getMessage());
                            return null;
                        });
                // Acknowledge to avoid retry loop
                ack.acknowledge();
            }
        }
    }

    private Optional<Integer> extractRetryCount(Headers headers) {
        Header header = headers.lastHeader(RETRY_HEADER);
        if (header == null) return Optional.empty();
        try {
            String val = new String(header.value(), StandardCharsets.UTF_8);
            return Optional.of(Integer.parseInt(val));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Replace this method with real business processing.
     * Throw an exception to test retry & DLQ behavior.
     */
    private void processMessage(String key, String payload) {
        // example: treat messages containing "fail" as errors
        if (payload != null && payload.toLowerCase().contains("fail")) {
            throw new IllegalStateException("simulated processing error for payload: " + payload);
        }
        // otherwise "process" (no-op)
    }

    public int getProcessedCount() {
        return processed.get();
    }
}
