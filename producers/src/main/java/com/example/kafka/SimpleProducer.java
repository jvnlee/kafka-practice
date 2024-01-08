package com.example.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

import static org.apache.kafka.clients.producer.ProducerConfig.*;

public class SimpleProducer {
    public static void main(String[] args) {
        String topic = "simple-topic";

        // KafkaProducer 환경 설정
        Properties props = new Properties();
        props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // KafkaProducer 인스턴스 생성
        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);

        // ProducerRecord 인스턴스 생성
        ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "k1", "v1");

        // KafkaProducer 레코드 전송
        kafkaProducer.send(producerRecord);

        // KafkaProducer 닫아주기 (자원 낭비 방지)
        // 닫히기 전에 flush()도 내부적으로 호출해서 남아있는 레코드가 모두 전송된 후에 닫히게끔 되어있음
        kafkaProducer.close();
    }
}
