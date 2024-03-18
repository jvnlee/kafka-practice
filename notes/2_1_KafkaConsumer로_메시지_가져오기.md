# 2. KafkaConsumer

## 2-1. KafkaConsumer로 메시지 가져오기

### 1) KafkaConsumer 인스턴스 생성

`Properties` 인스턴스에 key-value 형태의 환경설정 항목들을 세팅하고 이 세팅 내용을 `KafkaConsumer` 생성자에 전달해서 Producer를 생성함.

```java
// KafkaConsumer 환경 설정
Properties props = new Properties();
props.setProperty(BOOTSTRAP_SERVERS_CONFIG, "x.x.x.x:9092"); // Broker 주소
props.setProperty(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); // 레코드 key 역직렬화에 사용할 Serializer 클래스
props.setProperty(VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName()); // 레코드 value 역직렬화에 사용할 Serializer 클래스
props.setProperty(GROUP_ID_CONFIG, "group_01"); // Consumer가 속할 Consumer Group의 이름고

// KafkaConsumer 인스턴스 생성
KafkaConsumer<String, String> kafkaConsumer = new KafkaConsumer<>(props);
```

이 때 `KafkaConsumer`의 제네릭 타입에는 추후 읽어올 레코드의 key-value 타입을 적용함.
> 예시 코드의 경우에는 key(string)-value(string)

&nbsp;

### 2) topic 구독

읽어오고자 하는 메시지가 속해 있는 topic을 명시해서 `subscribe()` 메서드를 호출함

```java
String topic = "simple-topic";

kafkaConsumer.subscribe(List.of(topic));
```

예시에서는 토픽을 1개만 사용했지만, `subscribe()`의 인자는 컬렉션 타입이기 때문에 한 번에 여러 개를 묶어서 구독시킬 수 있음.

&nbsp;

### 3) poll 작업을 반복 수행

`while`문과 같은 루프를 이용해서 반복적으로 `poll()` 메서드를 호출함

```java
while (true) {
    ConsumerRecords<String, String> consumerRecords = kafkaConsumer.poll(ofMillis(1000)); // 최대 1000ms 대기 후에도 가져올 메시지가 없으면 빈 컬렉션 반환
    ...
}
```

이 때, `poll()`의 인자는 timeout 개념이 포함된 `Duration` 타입을 사용함

> 과거 버전에서는 대기 시간을 나타내기 위해 단순히 `long` 타입의 인자를 사용했으나, 이는 무기한 블락킹 이슈가 있어 변경됨. [(KIP-266 참고)](https://cwiki.apache.org/confluence/display/KAFKA/KIP-266%3A+Fix+consumer+indefinite+blocking+behavior#2)
> 
> > 기존의 `poll()`은 호출 시, 메타 데이터 업데이트가 필요할 경우 Broker에게 요청을 보내고 메타 데이터를 담은 응답이 돌아오기를 기다렸음.
> >
> >이 때 Broker에 문제가 생긴 경우 Consumer가 무기한으로 대기하게 되는 치명적인 이슈가 있었음.
> >
> > 현재의 `poll()`은 똑같이 메타 데이터 요청은 하되, 인자로 지정한 timeout 안에 응답을 못 받을 경우 빈 컬렉션을 반환하고 메서드가 종료되도록 바뀜.

`poll()`의 반환 타입인 `ConsumerRecords`는 `ConsumerRecord` 여러 개를 담을 수 있는 커스텀 컬렉션으로, 내부에 `Map` 형태로 데이터를 가지고 있음. 

```java
public class ConsumerRecords<K, V> implements Iterable<ConsumerRecord<K, V>> {
    ...
    private final Map<TopicPartition, List<ConsumerRecord<K, V>>> records;
    ...
}
```

`ConsumerRecords`를 순회할 경우, `List<ConsumerRecord<K, V>>`를 순회해서 개별 `ConsumerRecord`를 가지고 작업할 수 있음
 
```java
// ConsumerRecords의 iterator() 메서드
@Override
public Iterator<ConsumerRecord<K, V>> iterator() {
    return new ConcatenatedIterable<>(records.values()).iterator();
}
```

&nbsp;

### 4) KafkaConsumer 인스턴스 닫기

Consumer 클라이언트는 I/O 작업을 하는 (컴퓨팅 자원을 사용하는) 프로그램이기 때문에 사용 후에 반드시 닫아주어야 함

그런데 `poll()` 호출을 위해 열어둔 `while`문이 무한히 돌고 있기 때문에 일반적인 방법으로는 종료하기가 어려움

> 실행 중인 메인 쓰레드를 강제 종료시키면 Broker는 Consumer가 죽었다는 사실을 바로 알 수는 없음
> 
> 일정 시간이 지난 후에 Consumer로부터 heart beat가 더 이상 오지 않는 것을 감지한 후에서야 Consumer Group에서 제거시킴

그래서 *shutdown hook*을 사용해서 메인 쓰레드가 종료될 때 Consumer가 즉시 적절한 종료 절차를 밟을 수 있도록 함

> *shutdown hook*: 초기화는 되어있으나 실행되지 않고 대기하다가 VM이 종료될 때 호출되는 쓰레드

```java
Thread mainThread = Thread.currentThread(); // Consumer 인스턴스를 실행시키고 있는 메인 쓰레드

Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    kafkaConsumer.wakeup(); // wakeup 플래그를 true로 돌림

    try {
        mainThread.join(); // 메인 쓰레드가 죽기 전까지 대기 (close 메서드 호출을 위해)
    } catch (InterruptedException e) {
        ...
    }
}));
```

`wakeup` 플래그를 `true`로 돌리면 바로 다음 `poll()` 메서드가 호출될 때, 해당 플래그를 체크하고 `WakeupException`을 발생시킴

그러면 `while`문에서 빠져나올 수 있게 되고, Consumer의 `close()` 메서드를 호출해 정상적으로 자원을 반납하고 종료할 수 있게 됨

```java
try {
    while (true) {
        ConsumerRecords<String, String> consumerRecords = kafkaConsumer.poll(ofMillis(1000));
        ...
    }
} catch (WakeupException e) {
    ...
} finally {
    kafkaConsumer.close();
}
```