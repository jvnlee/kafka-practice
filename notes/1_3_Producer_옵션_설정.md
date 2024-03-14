# 1. KafkaProducer

## 1-3. Producer 옵션 설정

`ProducerConfig`를 통해 `KafkaProducer`의 작동 방식을 결정하는 옵션을 설정할 수 있음

<img alt="kafka_options" src="https://github.com/jvnlee/kafka-practice/assets/76623442/9b20be72-db48-4965-999c-09c8b93322ce">

&nbsp;

### 1) RecordAccumulator 관련 옵션

- `batch.size`: 단일 `RecordBatch`의 용량

    > 기본값은 16384 (약 16KB)

- `buffer.memory`: `RecordBatch`들을 담고 있는 `RecordAccumulator` 컴포넌트의 총 용량

    > 기본값은 33554432 (약 32MB)

- `max.block.ms`:`send()` 호출을 했는데 `RecordAccumulator`가 가득 찬 경우, 메서드를 블락시켜 대기하는 시간

&nbsp;

### 2) Sender 관련 옵션

- `linger.ms`: `Sender` 쓰레드가 `RecordAccumulator`로부터 데이터를 가져와서 전송을 하기 전 대기하는 시간

    > 기본값은 0 (대기하지 않음)
    > 
    > 이 옵션은 전송 효율을 최대한 높이기 위해 하나의 배치를 최대한 가득 채워 전송시키는 것을 지향함
    > 
    > 따라서 Sender가 가져온 배치가 이미 꽉 찬 경우 (즉, `batch.size`만큼 레코드가 채워진 경우) `linger.ms` 값과 관계 없이 즉시 전송됨

- `request.timeout.ms`: `Sender` 쓰레드가 브로커에게 메시지 전송 후, ack 응답을 받기 위해 대기하는 시간

  > 해당 시간 안에 ack 응답을 받지 못한 경우, 재전송을 시도하거나 예외를 발생시켜 예외 처리를 함

- `delivery.timeout.ms`: `send()` 메서드 호출로 인한 전체 전송 작업에 할당된 최대 시간

  > 전송 및 응답 대기, 그리고 재전송에 쓰이는 총 시간
  > 
  > `delivery.timeout.ms` >= `linger.ms` + `request.timeout.ms`

- `max.inflight.request.per.connection`: `Sender` 쓰레드가 브로커에게 한번에 보낼 수 있는 배치의 수

  > 기본값은 5 (1 ~ 5 사이의 값을 사용)
  >
  > 이 옵션이 0보다 큰 경우, Producer가 배치를 전송한 순서와 브로커에 적재되는 순서가 다를 수 있음
  > 
  > > 이는 브로커에 write 되는 과정에서 문제가 발생하면 재전송하고 다시 write 하면서 발생함

- `acks`: Producer가 전송한 메시지에 대한 Broker의 수신 확인 수준

  > #### 1) acks = 0
  >
  > Producer는 Leader Broker에게 메시지를 전송한 후, ack 응답을 받지 않음
  > 
  > 따라서 메시지가 유실되더라도 해당 사실을 확인하지 못하고, 재전송도 하지 않음
  > 
  > 메시지 유실 우려가 있는 대신, Producer는 ack 응답 수신을 생략하고 계속해서 메시지를 보낼 수 있으므로 전송 속도는 가장 빠름.
  > 
  > &nbsp;
  > 
  > #### 2) acks = 1
  > 
  > Producer는 Leader Broker에게 메시지를 전송한 후, ack 응답을 받음
  > 
  > ack 응답을 받은 후에야 다음 메시지를 전송하고, 받지 못하면 재전송함
  > 
  > 단, Leader Broker가 ack 응답을 보낸 다음 자신의 Follower들에게 복제를 하는 과정에서 fail하면 메시지 유실이 생길 수 있음
  > 
  > > ex) offset 999번 메시지가 Leader Broker에 도착하고, 이에 대한 ack 응답을 Producer에게 이미 보낸 상황
  > >
  > > Leader Broker가 Follower들에게 999번 메시지를 미처 다 복제해주기 전에 fail 함
  > >
  > > Kafka는 Follower 중에서 새 Leader를 선출했으나, 해당 Broker에는 999번 메시지가 도달하지 않았으므로 998번까지만 존재함.
  > >
  > > 이 상태에서 Producer가 offset 1000번 메시지를 보내면 새 Leader에게는 "..., 998, (유실), 1000"으로 999번이 유실된 상태가 됨
  > 
  > &nbsp;
  > 
  > #### 3) acks = all (또는 -1)
  > 
  > Kafka의 acks 기본 옵션.
  > 
  > Producer가 Leader Broker에게 메시지를 전송하고, Leader Broker는 `min.insync.replicas`*의 값만큼 Follower들에게 복제를 수행함.
  > 
  > > *`min.insync.replicas`: 복제를 통한 동기화가 이루어져야 하는 최소 Follower Broker의 수
  > >
  > > ex) 해당 값이 2면 Leader를 제외하고 최소 2개의 Follower가 Leader와 메시지 동기화가 되어있어야 함
  > 
  > 복제가 모두 성공적으로 수행되고 난 다음, Leader Broker는 Producer에게 ack 응답을 보냄
  > 
  > Producer는 복제까지 모두 이루어진 후의 ack 응답을 받고나서야 다음 메시지를 전송할 수 있음
  > 
  > 메시지 유실 방지를 매우 중시하는 옵션이지만, 그만큼 확인을 위해 기다려야 하는 시간이 길어져 전송 속도가 낮음