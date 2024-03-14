# 1. KafkaProducer의 내부 메커니즘

## 1-1. Producer의 레코드 전송 프로세스

### 1) KafkaProducer 인스턴스 생성

`Properties` 인스턴스에 key-value 형태의 환경설정 항목들을 세팅하고 이 세팅 내용을 `KafkaProducer` 생성자에 전달해서 Producer를 생성함.

```java
// KafkaProducer 환경 설정
Properties props = new Properties();
props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "192.168.56.101:9092"); // Broker 주소 (예시는 하나지만, 보통은 여러개)
props.setProperty(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // 레코드 key 직렬화에 사용할 Serializer 클래스
props.setProperty(VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()); // 레코드 value 직렬화에 사용할 Serializer 클래스

// KafkaProducer 인스턴스 생성
KafkaProducer<String, String> kafkaProducer = new KafkaProducer<>(props);
```

이 때 `KafkaProducer`의 제네릭 타입에는 추후 사용할 레코드의 key-value 타입을 적용함.
> 예시 코드의 경우에는 key(string)-value(string)

&nbsp;

📌 `KafkaProducer` 인스턴스가 생성될 때, 추후 실제로 레코드를 전송하는 역할을 할 별도의 Sender 쓰레드가 생성됨.

Sender 쓰레드는 내부 레코드 버퍼를 지속적으로 모니터링하면서 버퍼가 채워지면 Broker들에게 전송하는 작업을 함. (Batch 방식)

> 전송 작업은 비동기 방식으로 이루어지기 때문에 메인 쓰레드의 실행에 영향을 주지 않음.

&nbsp;

📌 `KafkaProducer` 인스턴스는 thread-safe하게 설계되어 있기 때문에 여러 쓰레드에서 한 인스턴스를 공유하여 사용할 수 있음

> 그러나 이는 멀티 쓰레드 환경에서 레코드의 전송 순서 보장을 해주지는 않으므로, 순서 보장이 필요한 경우에는 별도의 케어가 필요함

&nbsp;

### 2) ProducerRecord 인스턴스 생성

생성해놓은 `KafkaProducer`의 제네릭 타입과 동일하게 레코드 인스턴스를 생성함

이 때 `ProducerRecord`의 생성자는 여러가지가 있으나, 최소 구성 요소는 Topic 이름과 value 값임.

```java
String topic = "simple-topic";
ProducerRecord<String, String> producerRecord = new ProducerRecord<>(topic, "k1", "v1");
```

`ProducerRecord`를 구성하는 필드

```java
public class ProducerRecord<K, V> {
    
    private final String topic;
    private final Integer partition;
    private final Headers headers;
    private final K key;
    private final V value;
    private final Long timestamp;
    
    ...
}
```

> 생성 단계에서 명시되지 않은 구성 요소는 자동적으로 `null`로 들어감

&nbsp;

### 3) KafkaProducer의 send() 메서드 호출

생성해놓은 `ProducerRecord` 인스턴스를 producer의 `send()` 메서드의 파라미터로 전달

```java
kafkaProducer.send(producerRecord);
```