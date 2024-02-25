package com.example.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class CustomCallback implements Callback {

    public static final Logger logger = getLogger(CustomCallback.class);

    private final int seq;

    public CustomCallback(int seq) {
        this.seq = seq;
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
        if (exception == null) {
            logger.info(
                    "seq:{} partition:{} offset:{}",
                    this.seq,
                    metadata.partition(),
                    metadata.offset()
            );
        } else {
            logger.error(
                    "\n##### exception occurred #####\n" +
                    exception.getMessage()
            );
        }
    }

}
