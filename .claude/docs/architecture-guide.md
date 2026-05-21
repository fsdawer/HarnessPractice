# 아키텍처 의사결정 가이드 — CutIng

## 핵심 설계 원칙
1. 트랜잭션 범위 최소화 — DB 작업 구간에만 커넥션 점유
2. 읽기/쓰기 분리 — 조회는 Redis 캐시, 쓰기는 DB + 이벤트 발행
3. 비동기 후처리 — 랭킹·알림은 Redis Streams로 분리
4. 확장 고려 — 분산락은 Redis 기반 (서버 증설 시에도 동작)

## 기술 선택 이력

| 문제 | 고려한 대안 | 선택 | 이유 |
|------|-----------|------|------|
| 동시 예약 방지 | DB Lock, 낙관적 락 | Redis 분산락(SETNX) | 다중 서버 확장 고려, 커넥션 점유 최소화 |
| 랭킹 정렬 | DB ORDER BY | Redis ZSET | 조회 O(log N), DB 집계 연산 제거 |
| 후처리 비동기 | @Async, Spring Event | Redis Streams | 영속성, ACK 기반 재처리, 서버 재시작 내구성 |
| 실시간 알림 | WebSocket, Polling | SSE | 단방향 알림에 적합, HTTP/2 호환, 구현 단순 |
| 검색 | Elasticsearch | JPA Specification | 필터 5개 수준, 별도 인프라 불필요 |
| 채팅 | SSE | WebSocket + Redis Pub/Sub | 양방향 통신 필요 |

## 현재 구조 한계 및 다음 단계

| 현재 한계 | 다음 단계 |
|----------|----------|
| SseEmitter 서버 메모리 저장 → 다중 서버 불가 | Redis Pub/Sub으로 서버 간 이벤트 전파 |
| 단일 DB → 읽기 부하 증가 시 병목 | Read Replica 도입 |
| Redis 단일 노드 → SPOF | Redis Sentinel 또는 Cluster |
| Streams Consumer 단일 → 처리 지연 | Consumer Group 수평 확장 |

## architecture-agent 호출 예시
```
"[기능명] 구조 검토해줘"
"Redis vs DB 어떤 게 나은지 분석해줘"
"현재 아키텍처 병목 찾아줘"
```
