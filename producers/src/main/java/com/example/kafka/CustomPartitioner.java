package com.example.kafka;

import org.apache.kafka.clients.producer.Partitioner;
import org.apache.kafka.common.Cluster;
import org.apache.kafka.common.InvalidRecordException;

import java.util.Map;

import static org.apache.kafka.common.utils.Utils.murmur2;
import static org.apache.kafka.common.utils.Utils.toPositive;

public class CustomPartitioner implements Partitioner {

    private String specialKeyName;

    @Override
    public int partition(String topic, Object key, byte[] keyBytes, Object value, byte[] valueBytes, Cluster cluster) {
        int pNum = cluster.partitionsForTopic(topic).size(); // 전체 파티션 개수
        int pNumForSpecialKey = pNum / 2; // 전체 파티션의 절반은 특수한 키를 가진 레코드를 위해 할당
        int pNumForNonSpecialKey = pNum - pNumForSpecialKey; // 그 외의 파티션은 나머지 레코드를 위해 할당

        if (keyBytes == null) {
            throw new InvalidRecordException("Keys should not be null");
        }

        return key.equals(specialKeyName)
                ? toPositive(murmur2(valueBytes)) % pNumForSpecialKey
                : toPositive(murmur2(keyBytes)) % pNumForNonSpecialKey + pNumForSpecialKey;
    }

    @Override
    public void close() {

    }

    @Override
    public void configure(Map<String, ?> configs) {
        specialKeyName = configs.get("special.key").toString();
    }

}
