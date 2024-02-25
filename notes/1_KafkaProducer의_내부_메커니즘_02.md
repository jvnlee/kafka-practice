# 1. KafkaProducer의 내부 메커니즘

## 1-2. acks 옵션에 따른 전송 방식

### 1) acks = 0

Producer는 Leader Broker에게 메시지를 전송한 후, ack 응답을 받지 않음

따라서 메시지가 유실되더라도 해당 사실을 확인하지 못하고, 재전송도 하지 않음

메시지 유실 우려가 있는 대신, Producer는 ack 응답 수신을 생략하고 계속해서 메시지를 보낼 수 있으므로 전송 속도는 가장 빠름.

&nbsp;

### 2) acks = 1

Producer는 Leader Broker에게 메시지를 전송한 후, ack 응답을 받음

ack 응답을 받은 후에야 다음 메시지를 전송하고, 받지 못하면 재전송함

단, Leader Broker가 ack 응답을 보낸 다음 자신의 Follower들에게 복제를 하는 과정에서 fail하면 메시지 유실이 생길 수 있음

> ex) offset 999번 메시지가 Leader Broker에 도착하고, 이에 대한 ack 응답을 Producer에게 이미 보낸 상황
> 
> Leader Broker가 Follower들에게 999번 메시지를 미처 다 복제해주기 전에 fail 함
> 
> Kafka는 Follower 중에서 새 Leader를 선출했으나, 해당 Broker에는 999번 메시지가 도달하지 않았으므로 998번까지만 존재함.
> 
> 이 상태에서 Producer가 offset 1000번 메시지를 보내면 새 Leader에게는 "..., 998, (유실), 1000"으로 999번이 유실된 상태가 됨

&nbsp;

### 3) acks = all (acks = -1)

Kafka의 acks 기본 옵션.

Producer가 Leader Broker에게 메시지를 전송하고, Leader Broker는 `min.insync.replicas`의 값만큼 Follower들에게 복제를 수행함.

> `min.insync.replicas`: 복제를 통한 동기화가 이루어져야 하는 최소 Follower Broker의 수
> 
> ex) 해당 값이 2면 Leader를 제외하고 최소 2개의 Follower가 Leader와 메시지 동기화가 되어있어야 함

복제가 모두 성공적으로 수행되고 난 다음, Leader Broker는 Producer에게 ack 응답을 보냄

Producer는 복제까지 모두 이루어진 후의 ack 응답을 받고나서야 다음 메시지를 전송할 수 있음

메시지 유실 방지를 매우 중시하는 옵션이지만, 그만큼 확인을 위해 기다려야 하는 시간이 길어져 전송 속도가 낮음