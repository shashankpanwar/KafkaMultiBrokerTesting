package com.example.kafkatesting.constants;

public final class KafkaTopics {

    private KafkaTopics() {
        // Prevent instantiation
    }

    // Topic for ProducerOne and ConsumerOne
    public static final String TOPIC_ONE = "topic-one";
    public static final String TOPIC_GROUP_ONE = "group-one";
    public static final String TOPIC_ONE_PREFIX = "consumer-one-";

    // Topic for ProducerTwo and ConsumerTwo
    public static final String TOPIC_TWO = "topic-two";
    public static final String TOPIC_GROUP_TWO = "group-two";
    public static final String TOPIC_TWO_PREFIX = "consumer-two-";

    // dead-letter queue (DLQ)
    public static final String TOPIC_DLQ = "topic-dlq";
    public static final String TOPIC_DLQ_GROUP = "group-dlq";

    /** Example: notification events */
    public static final String TOPIC_NOTIFICATION = "topic-notification";


}