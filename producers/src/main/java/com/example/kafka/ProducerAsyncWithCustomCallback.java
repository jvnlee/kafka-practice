package com.example.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.IntegerSerializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Properties;

import static org.apache.kafka.clients.producer.ProducerConfig.*;

public class ProducerAsyncWithCustomCallback {

    public static void main(String[] args) {
        String topic = "multipart-topic";

        Properties props = new Properties();
        props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(KEY_SERIALIZER_CLASS_CONFIG, IntegerSerializer.class.getName());
        props.setProperty(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<Integer, String> kafkaProducer = new KafkaProducer<>(props);

        for (int seq = 0; seq < 20; seq++) {
            ProducerRecord<Integer, String> producerRecord = new ProducerRecord<>(topic, seq, "value#" + seq);

            Callback callback = new CustomCallback(seq);

            kafkaProducer.send(producerRecord, callback);
        }

        kafkaProducer.close();
    }
}
