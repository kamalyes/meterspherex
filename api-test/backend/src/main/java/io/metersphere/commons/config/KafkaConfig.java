package io.metersphere.commons.config;

import io.metersphere.commons.constants.KafkaTopicConstants;
import io.metersphere.commons.utils.CommonBeanFactory;
import io.metersphere.config.KafkaProperties;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfig {

    @Resource
    private KafkaProperties kafkaProperties;

    @Bean
    public NewTopic apiExecTopic() {
        return TopicBuilder.name(KafkaTopicConstants.API_REPORT_TOPIC)
                .build();
    }

    @Bean
    public NewTopic testPlanMessageTopic() {
        return TopicBuilder.name(KafkaTopicConstants.TEST_PLAN_REPORT_TOPIC)
                .build();
    }

    public static Map<String, Object> getKafka() {
        KafkaProperties kafkaProperties = CommonBeanFactory.getBean(KafkaProperties.class);
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        producerProps.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, kafkaProperties.getMaxRequestSize());
        return producerProps;
    }

    public Map<String, Object> consumerConfigs() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        producerProps.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, kafkaProperties.getMaxRequestSize());
        // ?????????????????????????????????
        producerProps.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);

        // ??????????????????kafka????????????????????????????????????????????????????????????????????????reBalance
        producerProps.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 900000);
        // ????????????
        producerProps.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 5000);
        // ???????????? ?????? false
        producerProps.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        producerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        producerProps.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, 5000);

        producerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        producerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return producerProps;
    }

    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, Object>> batchFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfigs()));
        //????????????
        factory.setConcurrency(1);
        //??????????????????
        factory.setBatchListener(true);

        factory.getContainerProperties().setPollTimeout(5000L);

        //?????????????????????????????????
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        return factory;
    }
}
