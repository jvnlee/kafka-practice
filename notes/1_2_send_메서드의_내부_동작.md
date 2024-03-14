# 1. KafkaProducer

## 1-2.️ `send()` 메서드의 내부 동작

`send()`는 동기 방식이냐 비동기 방식이냐에 따라 두 종류가 존재함

1. `send(ProducerRecord<K, V> record)`:

- 동기 방식

- 메시지를 전송하면, Broker로부터 ack 응답을 받을 때까지 block 상태

2. `send(ProducerRecord<K, V> record, Callback callback)`

- 비동기 방식

- 메시지를 전송하면, Broker로부터 ack 응답을 기다리지 않고 다음 작업을 계속함 (non-block)

- 나중에 ack 응답을 받으면, 그 때 Callback이 호출되어 동작함 (metadata와 exception 핸들링)

 
1번 `send()`는 내부에서 Callback을 `null`로 두고 2번 `send()`를 호출함

따라서 메시지 전송 프로세스 자체는 2번 `send()` 내부에서 호출되는 `doSend()`라는 메서드를 뜯어보면 알 수 있음

&nbsp;

### 1) Kafka Cluster로부터 Topic에 대한 메타 데이터 요청

실제 전송에 앞서, 레코드를 전송할 Topic에 대한 정보를 업데이트함 (`ProducerMetadata` 갱신)

```java
try {
    clusterAndWaitTime = waitOnMetadata(record.topic(), record.partition(), nowMs, maxBlockTimeMs);
}
```

&nbsp;

### 2) 직렬화(Serialization)

Configuration에 등록한 Serializer로 key와 value를 직렬화(byte array로 변환)해서 전송할 레코드에 담음

```java
byte[] serializedKey;
try {
    serializedKey = keySerializer.serialize(record.topic(), record.headers(), record.key());
} catch (ClassCastException cce) {
    throw new SerializationException("Can't convert key of class " + record.key().getClass().getName() +
            " to class " + producerConfig.getClass(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG).getName() +
            " specified in key.serializer", cce);
}
byte[] serializedValue;
try {
    serializedValue = valueSerializer.serialize(record.topic(), record.headers(), record.value());
} catch (ClassCastException cce) {
    throw new SerializationException("Can't convert value of class " + record.value().getClass().getName() +
            " to class " + producerConfig.getClass(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG).getName() +
            " specified in value.serializer", cce);
}
```

&nbsp;

### 3) 파티셔닝(Partitioning)

레코드의 key 유무에 따라 각기 다른 전략을 사용해 어느 파티션에 레코드를 보낼지 지정함.

```java
// Try to calculate partition, but note that after this call it can be RecordMetadata.UNKNOWN_PARTITION,
// which means that the RecordAccumulator would pick a partition using built-in logic (which may
// take into account broker load, the amount of data produced to each partition, etc.).
int partition = partition(record, serializedKey, serializedValue, cluster);
```

레코드에 key가 존재하면 key 해싱으로 파티셔닝하고 key가 없으면 우선 파티션을 `UNKNOWN_PARTITION`으로 지정해놓고 이후에 `RecordAccumulator`가 파티션을 정하게 함.

```java
private int partition(ProducerRecord<K, V> record, byte[] serializedKey, byte[] serializedValue, Cluster cluster) {
    if (record.partition() != null)
        return record.partition();

    if (partitioner != null) {
        int customPartition = partitioner.partition(
            record.topic(), record.key(), serializedKey, record.value(), serializedValue, cluster);
        if (customPartition < 0) {
            throw new IllegalArgumentException(String.format(
                "The partitioner generated an invalid partition number: %d. Partition number should always be non-negative.", customPartition));
        }
        return customPartition;
    }

    if (serializedKey != null && !partitionerIgnoreKeys) {
        // hash the keyBytes to choose a partition
        return BuiltInPartitioner.partitionForKey(serializedKey, cluster.partitionsForTopic(record.topic()).size());
    } else {
        return RecordMetadata.UNKNOWN_PARTITION;
    }
}
```

아래는 `RecordAccumulator`의 `append()` 중 일부로, `UNKNOWN_PARTITION`을 가진 레코드인 경우 직접 브로커 가용성과 성능을 고려해 Sticky Partitioning을 한다는 것을 알 수 있음.

```java
// If the message doesn't have any partition affinity, so we pick a partition based on the broker
// availability and performance.  Note, that here we peek current partition before we hold the
// deque lock, so we'll need to make sure that it's not changed while we were waiting for the
// deque lock.
final BuiltInPartitioner.StickyPartitionInfo partitionInfo;
final int effectivePartition;
if (partition == RecordMetadata.UNKNOWN_PARTITION) {
    partitionInfo = topicInfo.builtInPartitioner.peekCurrentPartitionInfo(cluster);
    effectivePartition = partitionInfo.partition();
} else {
    partitionInfo = null;
    effectivePartition = partition;
}
```

> **요약**
>
> 📌 key가 없는 레코드인 경우:
>
> Sticky Partitioning 전략으로 파티션 넘버를 지정함.
> 
> Sticky Partitioning이란, 배치를 최대한 효율적으로 사용하기 위해 배치를 가득 채우고 특정 파티션으로 보내 채워 나가는 전략
> 
> <img src="https://cdn.confluent.io/wp-content/uploads/sticky-partitioner-strategy.png" height="480">
>
> &nbsp;
> 
> 📌 key를 가진 레코드인 경우:
>
> 직렬화된 key를 해싱해서 파티션 넘버를 지정함. 동일한 key를 가진다면 동일 파티션으로 간다는 의미.

&nbsp;

### 4) `RecordAccumulator`에 레코드 적재

`RecordAccumulator`는 브로커에 배치 전송을 하기 위해 전송할 레코드들을 모아두는 버퍼.

레코드를 개별로 전송하게 되면 네트워크 자원 낭비가 발생하고, 전송 효율이 떨어지기 때문에 Producer는 레코드를 배치 방식으로 전송함

```java
RecordAccumulator.RecordAppendResult result = accumulator
        .append(record.topic(), partition, timestamp, serializedKey, serializedValue, headers, appendCallbacks, remainingWaitMs, abortOnNewBatch, nowMs, cluster);
```

레코드를 적재하는 역할을 하는 `append()` 메서드

```java
public RecordAppendResult append(String topic, int partition, long timestamp, byte[] key,
                                     byte[] value, Header[] headers, AppendCallbacks callbacks, long maxTimeToBlock,
                                     boolean abortOnNewBatch, long nowMs, Cluster cluster) throws InterruptedException {
    TopicInfo topicInfo = topicInfoMap.computeIfAbsent(topic, k -> new TopicInfo(logContext, k, batchSize));
    
    try {
        // Loop to retry in case we encounter partitioner's race conditions.
        while (true) {
            ...
        
            Deque<ProducerBatch> dq = topicInfo.batches.computeIfAbsent(effectivePartition, k -> new ArrayDeque<>());
            synchronized (dq) {
                // After taking the lock, validate that the partition hasn't changed and retry.
                if (partitionChanged(topic, topicInfo, partitionInfo, dq, nowMs, cluster))
                    continue;

                RecordAppendResult appendResult = tryAppend(timestamp, key, value, headers, callbacks, dq, nowMs);
                if (appendResult != null) {
                    // If queue has incomplete batches we disable switch (see comments in updatePartitionInfo).
                    boolean enableSwitch = allBatchesFull(dq);
                    topicInfo.builtInPartitioner.updatePartitionInfo(partitionInfo, appendResult.appendedBytes, cluster, enableSwitch);
                    return appendResult;
                }
            }

            ...
        }
    } finally {
        free.deallocate(buffer);
        appendsInProgress.decrementAndGet();
    }
}
```

여기서 `TopicInfo`는 `RecordAccumulator`의 내부 클래스로, `ConcurrentMap` 형태로 레코드 배치를 가지고 있음.

> `ConcurrentMap`을 사용하기 때문에 thread-safe함

```java
private static class TopicInfo {
    public final ConcurrentMap<Integer /*partition*/, Deque<ProducerBatch>> batches = new CopyOnWriteMap<>();
    public final BuiltInPartitioner builtInPartitioner;

    public TopicInfo(LogContext logContext, String topic, int stickyBatchSize) {
        builtInPartitioner = new BuiltInPartitioner(logContext, topic, stickyBatchSize);
    }
}
```

`batches`는 파티션 ID를 key로 갖기 때문에 동일한 토픽과 파티션 ID를 가지는 레코드들은 동일한 `ProducerBatch` 배치로 묶여서 전송됨.

&nbsp;

### 5) `Sender`를 통한 레코드 전송

`Sender`는 실제 레코드 전송을 담당하는 I/O 쓰레드로, `KafkaProducer`가 초기화될 때 함께 생성됨

```java
// KafkaProducer 생성자 내부 코드
this.sender = newSender(logContext, kafkaClient, this.metadata);
String ioThreadName = NETWORK_THREAD_PREFIX + " | " + clientId;
this.ioThread = new KafkaThread(ioThreadName, this.sender, true);
this.ioThread.start();
```

`doSend()` 말미에 보면 전송할 배치가 준비됐을 때, 이 `Sender` 쓰레드를 깨워 전송하게끔 함

```java
if (result.batchIsFull || result.newBatchCreated) {
    log.trace("Waking up the sender since topic {} partition {} is either full or getting a new batch", record.topic(), appendCallbacks.getPartition());
    this.sender.wakeup();
}
return result.future;
```

`Sender`는 `RecordAccumulator`로부터 전송을 기다리는 레코드들을 가져와서 Broker에게 보냄

```java
// Sender 클래스의 run() 메서드
@Override
public void run() {
    log.debug("Starting Kafka producer I/O thread.");

    // main loop, runs until close is called
    while (running) {
        try {
            runOnce();
        } catch (Exception e) {
            log.error("Uncaught error in kafka producer I/O thread: ", e);
        }
    }
    ...
}
```

`runOnce()` 내부에는 전송하는 역할을 담당하는 `sendProducerData()` 호출부가 있음

```java
// run()이 호출한 runOnce() 메서드
void runOnce() {
    ...
    long currentTimeMs = time.milliseconds();
    long pollTimeout = sendProducerData(currentTimeMs);
    client.poll(pollTimeout, currentTimeMs);
}
```

`RecordAccumulator`의 `drain()`을 호출해 전송할 레코드 배치를 가져오고 `sendProduceRequests()`를 호출해 전송함

```java
// runOnce()가 호출한 sendProducerData() 메서드
private long sendProducerData(long now) {
    Cluster cluster = metadata.fetch();
    // get the list of partitions with data ready to send
    RecordAccumulator.ReadyCheckResult result = this.accumulator.ready(cluster, now);
    
    ...

    // create produce requests
    Map<Integer, List<ProducerBatch>> batches = this.accumulator.drain(cluster, result.readyNodes, this.maxRequestSize, now);
    addToInflightBatches(batches);

    ...
        
    sendProduceRequests(batches, now);
    return pollTimeout;
}
```