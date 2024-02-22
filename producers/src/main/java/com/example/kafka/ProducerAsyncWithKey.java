package com.example.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

import java.util.Properties;

import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.slf4j.LoggerFactory.getLogger;

public class ProducerAsyncWithKey {
    public static final Logger logger = getLogger(ProducerAsyncWithKey.class);

    public static void main(String[] args) {
        String topic = "multipart-topic";

        Properties props = new Properties();
        props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);

        for (int seq = 0; seq < 20; seq++) {
            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "key#" + seq, "value#" + seq);

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
        }

        kafkaProducer.close();
    }
}
