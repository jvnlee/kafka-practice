package com.example.kafka;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.apache.kafka.clients.producer.ProducerConfig.*;
import static org.slf4j.LoggerFactory.getLogger;

public class PizzaProducerForCustomPartitioner {
    public static final Logger logger = getLogger(PizzaProducerForCustomPartitioner.class);

    public static final String topic = "pizza-topic-for-custom-partitioner";

    public static KafkaProducer<String, String> kafkaProducer;

    /**
     *
     * @param totalReqNum: 전송할 메시지의 총 개수
     * @param singleReqInterval: 메시지를 1건 전송할 때마다 발생시킬 시간차 (ms)
     * @param multiReqInterval: 메시지를 n건 전송할 때마다 발생시킬 시간차 (ms)
     * @param multiReqNum: n의 값
     * @param isSync: 메시지 전송 시 동기 방식 사용 여부
     */
    public static void sendPizzaMessage(int totalReqNum,
                                        int singleReqInterval,
                                        int multiReqInterval,
                                        int multiReqNum,
                                        boolean isSync) {
        PizzaMessage pizzaMessage = new PizzaMessage();

        for (int i = 1; i <= totalReqNum; i++) {
            Map<String, String> messageMap = pizzaMessage.produceMessage(i);

            Map.Entry<String, String> message = messageMap.entrySet().iterator().next();
            String key = message.getKey();
            String value = message.getValue();

            ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, key, value);

            sendMessage(producerRecord, key, isSync);

            if (multiReqNum > 0 && i % multiReqNum == 0) {
                try {
                    logger.info(
                            "\n### current iteration count: " + i +
                            "\n### sleeping for " + multiReqInterval + "ms"
                    );
                    Thread.sleep(multiReqInterval);
                } catch (InterruptedException e) {
                    logger.error(e.getMessage());
                }
            }

            if (singleReqInterval > 0) {
                try {
                    logger.info(
                            "\n### current iteration count: " + i +
                            "\n### sleeping for " + singleReqInterval + "ms"
                    );
                    Thread.sleep(singleReqInterval);
                } catch (InterruptedException e) {
                    logger.error(e.getMessage());
                }
            }
        }
    }

    public static void sendMessage(ProducerRecord<String, String> producerRecord,
                                   String messageKey,
                                   boolean isSync) {
        if (!isSync) {
            kafkaProducer.send(producerRecord, (metadata, exception) -> {
                if (exception == null) {
                    logger.info(
                            "\nasync message: " + messageKey +
                            "\npartition: " + metadata.partition() +
                            "\noffset: " + metadata.offset()
                    );
                } else {
                    logger.error(
                            "\n##### exception occurred #####\n" + exception.getMessage()
                    );
                }
            });
        } else {
            try {
                RecordMetadata metadata = kafkaProducer.send(producerRecord).get();

                logger.info(
                        "\nsync message: " + messageKey +
                        "\npartition: " + metadata.partition() +
                        "\noffset: " + metadata.offset()
                );
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }


    }

    public static void main(String[] args) {
        Properties props = new Properties();
        props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092");
        props.setProperty(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.setProperty(PARTITIONER_CLASS_CONFIG, "com.example.kafka.CustomPartitioner");
        props.setProperty("special.key", "P001");

        kafkaProducer = new KafkaProducer<>(props);

        sendPizzaMessage(1000, 10, 100, 100, true);

        kafkaProducer.close();
    }
}
