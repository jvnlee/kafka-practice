package com.example.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;

import java.util.List;
import java.util.Properties;

import static java.time.Duration.ofMillis;
import static org.apache.kafka.clients.consumer.ConsumerConfig.*;
import static org.slf4j.LoggerFactory.getLogger;

public class SimpleConsumerWithWakeup {

    public static final Logger logger = getLogger(SimpleConsumerWithWakeup.class);

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

        Thread mainThread = Thread.currentThread();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Main Thread is preparing shutdown. Shutdown hook has been called.");
            kafkaConsumer.wakeup();

            try {
                mainThread.join();
            } catch (InterruptedException e) {
                logger.error("InterruptedException has occurred.");
            }
        }));

        // poll 수행
        try {
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
        } catch (WakeupException e) {
            logger.error("WakeupException has occurred.");
        } finally {
            kafkaConsumer.close();
            logger.info("Consumer client has been closed.");
        }
    }

}
