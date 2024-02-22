package com.example.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

import java.util.Properties;

import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.slf4j.LoggerFactory.getLogger;

public class SimpleProducerAsync {
    public static final Logger logger = getLogger(SimpleProducerAsync.class);

    public static void main(String[] args) {
        String topic = "simple-topic";

        Properties props = new Properties();
        props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);

        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "value");

        // 비동기 방식으로 레코드 메타 데이터 받아오기
        // 레코드를 전송하고 나서 브로커의 응답을 대기하지 않고도 메인 쓰레드가 다음 작업을 할 수 있음.
        // send()에 콜백 메서드를 인자로 넘기는 방식을 사용, 브로커의 응답 데이터 또는 예외 발생 내역은 콜백의 인자에 담김
        kafkaProducer.send(producerRecord, (metadata, exception) -> {
            if (exception == null) {
                logger.info(
                    "\n##### record metadata received #####\n" +
                    "partition: " + metadata.partition() + "\n" +
                    "offset: " + metadata.offset() + "\n" +
                    "timestamp: " + metadata.timestamp()
                );
            } else {
                logger.error(
                    "\n##### exception occurred #####\n" +
                    exception.getMessage()
                );
            }
        });

        kafkaProducer.close();
    }
}
