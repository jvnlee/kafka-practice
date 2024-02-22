package com.example.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.slf4j.LoggerFactory.getLogger;

public class SimpleProducerSync {
    public static final Logger logger = getLogger(SimpleProducerSync.class);

    public static void main(String[] args) {
        String topic = "simple-topic";

        Properties props = new Properties();
        props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);

        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "value");

        try {
            // 동기 방식으로 레코드 메타 데이터 받아오기
            // 레코드를 전송하고 나서 브로커로부터 응답을 받을 때까지 대기한 후에 메인 쓰레드가 다음 작업을 할 수 있음.
            RecordMetadata recordMetadata = kafkaProducer.send(producerRecord).get();

            logger.info(
                    "\n##### record metadata received #####\n" +
                    "partition: " + recordMetadata.partition() + "\n" +
                    "offset: " + recordMetadata.offset() + "\n" +
                    "timestamp: " + recordMetadata.timestamp()
            );
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        } finally {
            kafkaProducer.close();
        }
    }
}
