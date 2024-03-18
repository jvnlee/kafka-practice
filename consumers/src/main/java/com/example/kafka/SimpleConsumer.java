package com.example.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Properties;

import static java.time.Duration.ofMillis;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.slf4j.LoggerFactory.getLogger;

public class SimpleConsumer {

    public static final Logger logger = getLogger(SimpleConsumer.class);

    public static void main(String[] args) {
        String topic = "simple-topic";

        // KafkaConsumer 환경 설정
        Properties props = new Properties();
        props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.setProperty(GROUP_ID_CONFIG, "group_01");

        // KafkaConsumer 인스턴스 생성
        KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(props);

        // topic 구독
        kafkaConsumer.subscribe(List.of(topic));

        // poll 수행
        while (true) {
            ConsumerRecords<String, String> consumerRecords = kafkaConsumer.poll(ofMillis(1000)); // 최대 1000ms 대기 후에도 가져올 메시지가 없으면 빈 컬렉션 반환

            for (ConsumerRecord<String, String> consumerRecord : consumerRecords) {
                logger.info(
                        "\nkey={} \nvalue={} \npartition={}",
                        consumerRecord.key(),
                        consumerRecord.value(),
                        consumerRecord.partition()
                );
            }
        }
        
        // 호출 불가능 (다른 방식으로 해결 예정)
        // kafkaConsumer.close();
    }

}
