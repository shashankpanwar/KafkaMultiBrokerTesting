package com.example.kafkatesting.configs;

import com.example.kafkatesting.constants.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConfig {
    @Bean
    public NewTopic topicOne() {
        return new NewTopic(KafkaTopics.TOPIC_ONE, 3, (short) 3);
    }

    @Bean
    public NewTopic topicTwo() {
        return new NewTopic(KafkaTopics.TOPIC_TWO, 3, (short) 3);
    }

    @Bean
    public NewTopic topicTwoDlq() {
        return new NewTopic(KafkaTopics.TOPIC_DLQ, 1, (short) 1);
    }


    // Custom listener factory for manual ack consumer
    @Bean("manualAckListenerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> manualAckListenerFactory(ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        // manual acknowledgment: user calls ack.acknowledge()
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // tune concurrency if you want parallel consumers
        factory.setConcurrency(1);

        return factory;
    }
}
