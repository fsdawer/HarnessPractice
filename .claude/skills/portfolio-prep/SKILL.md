---
name: portfolio-prep
description: CutIng 프로젝트의 포트폴리오 자료를 만들어줍니다. "포트폴리오", "면접 준비", "이 기능 어떻게 설명해?", "기술적 의사결정 설명해줘", "README 써줘", "프로젝트 소개", "면접 질문 뽑아줘" 같은 요청에 사용합니다.
---

# Portfolio Prep — CutIng 미용실 예약 플랫폼

## 프로젝트 기본 정보

**프로젝트명**: CutIng (커팅)  
**한 줄 소개**: Redis 기반 분산 처리와 실시간 알림을 갖춘 미용실 예약 플랫폼  
**기술 스택**: Spring Boot 4.0.3 / Java 17 / Vue.js 3 / MySQL / Redis / JWT

---

## 요청 유형별 대응

### 1. 프로젝트 전체 소개 요청

```
## 프로젝트 소개
[한 줄 요약]

## 핵심 기능
- 기능1: [기술적 포인트]
- 기능2: [기술적 포인트]

## 기술적 도전과 해결
[가장 인상적인 문제 2~3개]

## 배운 점
[구체적인 경험 — 수치 없는 "성능 향상" 같은 표현 금지]
```

### 2. 특정 기능 설명 요청

1. **문제 상황** — 왜 이 기술이 필요했는지
2. **고려한 대안들** — 다른 선택지와 트레이드오프
3. **선택 이유** — 이 방법을 고른 근거
4. **구현 결과** — 실제 코드 흐름

### 3. 기술적 의사결정 설명

"왜 Redis?", "Elasticsearch 안 쓴 이유?" 같은 질문에:
- "~를 고려했지만, ~이기 때문에 ~를 선택했다" 구조 사용
- 트레이드오프를 인식하고 있음을 보여주는 것이 핵심

---

## CutIng 핵심 기술 포인트

### 동시성 — Redis 분산락
```
문제: 같은 시간대에 여러 명이 동시 예약 시도 → 중복 예약
해결: Redis SETNX + TTL 5초
     키: lock:reservation:{stylistId}:{datetime}
결과: 선착순 1명만 락 획득 → 나머지는 409 Conflict
대안: DB Lock(성능↓, 커넥션 점유), 낙관적 락(충돌 시 재시도 UX 나쁨)
```

### 실시간 알림 — SSE + Redis Stream
```
흐름: 예약 이벤트 → Redis Stream → StreamListener → SSE 전송
     재연결 시 → Redis List에서 미전달 알림 복원 (7일 보관)
선택: WebSocket 대비 단방향이라 서버 부담 적음, HTTP/2 호환
```

### 검색 고도화 — JPA Specification
```
문제: keyword/district/category/minPrice/maxPrice 복합 동적 쿼리
해결: JpaSpecificationExecutor + Specification 패턴
     StylistServiceItem 1:N → EXISTS 서브쿼리 (DISTINCT 대신)
     @EntityGraph로 N+1 방지
선택: QueryDSL은 Spring Boot 4.x APT 설정 복잡, 필터 5개 수준에서 Specification으로 충분
```

### 랭킹 — Redis ZSET
```
알고리즘: 베이지안 추정 = reviewCount + avgRating + recentBookings(30일)
구조: ranking:{district} ZSET → ZREVRANGEBYSCORE → O(log N)
장점: DB 집계 없이 실시간 조회
```

### 빈자리 알림 — Redis Stream 이벤트
```
흐름: 예약 취소 → cancel_stream → CancelStreamListener → 대기자 WebSocket 알림
특징: Consumer Group으로 메시지 유실 방지, 비동기 처리
```

---

## 면접 질문 + 꼬리질문 + 관련 개념

사용자가 "면접 질문 뽑아줘"나 "면접 준비" 요청 시, 아래 구조로 출력하세요:

```
Q: [질문]
A: [핵심 답변 — 이 프로젝트 기준으로]

  꼬리질문 1: [예상 후속 질문]
  → 답변 방향: [어떻게 답해야 하는지]
  → 관련 개념: [개념명 + 한 줄 설명]

  꼬리질문 2: ...
```

### 동시성

**Q: 예약 중복 방지를 어떻게 구현했나요?**
A: Redis SETNX로 분산락을 구현했습니다. `lock:reservation:{stylistId}:{datetime}` 키로 락을 획득하고 TTL 5초를 설정해, 프로세스 비정상 종료 시에도 락이 자동 해제됩니다.

- 꼬리질문: "DB 트랜잭션만으로는 왜 안 되나요?"
  → 답변 방향: DB Lock은 커넥션을 점유하므로 동시 요청 많을 때 커넥션 풀 고갈 가능. 또한 다중 서버 환경에서 DB Lock은 같은 DB를 쓸 때만 동작하지만 분산락은 어느 인스턴스든 동일하게 적용.
  → 관련 개념: **낙관적 락(Optimistic Lock)** — 충돌 가정 없이 커밋 시점에 버전 비교. 충돌 빈도가 낮을 때 유리. CAS(Compare-And-Swap) 방식.

- 꼬리질문: "분산 환경에서 Redis도 단일 장애점이 될 수 있지 않나요?"
  → 답변 방향: Redis Sentinel(고가용성) 또는 Redis Cluster로 해결 가능. 현재 프로젝트는 단일 노드지만, 프로덕션이라면 Sentinel 구성 필요.
  → 관련 개념: **RedLock 알고리즘** — Redis 클러스터 환경에서 과반수 노드에 락을 동시 획득해 안전성 보장. Redisson 라이브러리가 구현체 제공.

---

**Q: 동시성 문제를 해결하는 다른 방법을 알고 있나요?**
A: 낙관적 락(버전 필드), 비관적 락(SELECT FOR UPDATE), 메시지 큐 직렬화, 분산락(Redis/ZooKeeper) 등이 있습니다. 이 프로젝트는 예약 충돌 빈도가 높을 수 있어 비관적 접근인 분산락을 선택했습니다.

- 꼬리질문: "Saga 패턴은 알고 있나요?"
  → 답변 방향: 분산 트랜잭션을 여러 로컬 트랜잭션의 체인으로 처리하는 패턴. 실패 시 보상 트랜잭션(Compensating Transaction)을 실행.
  → 관련 개념: **Saga 패턴** — 마이크로서비스 간 트랜잭션 일관성 유지. Choreography(이벤트 기반)와 Orchestration(중앙 조율자) 두 방식. 현재 프로젝트는 모놀리식이므로 미적용이지만, 마이크로서비스 전환 시 결제↔예약 간 Saga 필요.

---

### Redis 활용

**Q: Redis를 어떤 용도로 사용했나요?**
A: 6가지 용도로 사용했습니다. (1) JWT 블랙리스트, (2) 예약 시간대 캐시, (3) 채팅방 목록 캐시, (4) Pub/Sub 채팅 메시징, (5) rate limiting, (6) 랭킹 ZSET, (7) 분산락, (8) Stream 이벤트 처리.

- 꼬리질문: "Redis 장애 시 어떻게 대응하나요?"
  → 답변 방향: 캐시는 Cache-Aside 패턴으로 Miss 시 DB 폴백. 분산락 장애 시 DB 레벨 unique constraint가 마지막 방어선. Pub/Sub은 Stream으로 대체 가능.
  → 관련 개념: **Cache-Aside(Lazy Loading)** vs **Write-Through** — Cache-Aside는 읽기 시 캐시 없으면 DB 조회 후 적재. Write-Through는 쓰기 시 캐시와 DB 동시 갱신. 현재 프로젝트는 Cache-Aside 사용.

- 꼬리질문: "Redis의 데이터 영속성은 어떻게 보장하나요?"
  → 답변 방향: RDB(스냅샷)와 AOF(모든 쓰기 로그) 두 방식. 이 프로젝트의 Redis 데이터는 대부분 재생성 가능한 캐시라 영속성보다 성능 우선.
  → 관련 개념: **RDB vs AOF** — RDB는 빠른 복구, 데이터 손실 가능성 있음. AOF는 데이터 손실 최소, 파일 크고 복구 느림.

---

### 인증 / 보안

**Q: JWT 블랙리스트를 왜 Redis에 저장했나요?**
A: JWT는 서버리스 특성상 발급 후 만료 전 강제 무효화가 불가능합니다. 로그아웃 시 토큰을 Redis에 블랙리스트로 등록하고 남은 유효시간만큼 TTL을 설정해 자동 삭제합니다.

- 꼬리질문: "Refresh Token Rotation은 구현했나요?"
  → 답변 방향: 현재는 미구현. Access Token 만료 시 재로그인. 프로덕션이라면 Refresh Token을 Redis에 저장하고 사용 시 교체(Rotation)하는 방식 필요.
  → 관련 개념: **Refresh Token Rotation** — Refresh Token 사용 시마다 새 토큰 발급 + 기존 무효화. 토큰 탈취 감지 가능(기존 토큰 재사용 시 모든 세션 강제 종료).

- 꼬리질문: "OAuth2 소셜 로그인과 JWT를 어떻게 연동했나요?"
  → 답변 방향: OAuth2 인증 성공 후 Spring Security의 `OAuth2UserService`에서 사용자 정보를 DB에 저장/업데이트하고 자체 JWT를 발급. 이후 모든 API는 자체 JWT로 인증.

---

### 설계 / 아키텍처

**Q: SSE vs WebSocket 트레이드오프를 설명해주세요.**
A: SSE는 서버→클라이언트 단방향 스트리밍으로 HTTP 위에서 동작해 구현이 간단하고 자동 재연결을 지원합니다. WebSocket은 양방향이지만 별도 프로토콜 업그레이드가 필요합니다. 알림(단방향)은 SSE, 채팅(양방향)은 WebSocket으로 분리했습니다.

- 꼬리질문: "SSE 연결이 끊겼을 때 어떻게 처리했나요?"
  → 답변 방향: Redis List에 미전달 알림을 보관(7일 TTL). 재연결 시 `flushPending(userId)` 호출로 밀린 알림 일괄 전달.

- 꼬리질문: "대규모 트래픽에서 SSE 연결 수가 많아지면 어떻게 되나요?"
  → 답변 방향: SSE는 커넥션당 스레드를 점유하므로 서버 부담이 있음. 해결책으로 Non-blocking SSE(Flux, WebFlux), 또는 외부 Push 서비스(FCM, APNs) 사용 고려.
  → 관련 개념: **Long Polling vs SSE vs WebSocket** — Long Polling은 클라이언트가 주기적으로 요청, SSE는 서버가 밀어줌, WebSocket은 완전 양방향.

---

**Q: N+1 문제를 어떻게 해결했나요?**
A: JPA Specification 사용 시 `@EntityGraph(attributePaths = {"user", "salon"})`로 FETCH JOIN을 강제해 한 쿼리로 연관 엔티티를 함께 로드했습니다.

- 꼬리질문: "FETCH JOIN과 EntityGraph의 차이는?"
  → 답변 방향: FETCH JOIN은 JPQL에 직접 작성, EntityGraph는 메서드 레벨 어노테이션으로 재사용성 높음. 둘 다 결과적으로 JOIN 쿼리 생성.
  → 관련 개념: **Batch Size** — `@BatchSize(size=N)`으로 N+1을 IN 쿼리 1+1로 완화하는 대안. FETCH JOIN이 불가한 컬렉션 다중 페치 상황에 유용.

- 꼬리질문: "컬렉션을 2개 이상 FETCH JOIN하면 어떻게 되나요?"
  → 답변 방향: `MultipleBagFetchException` 발생. 해결책: 하나만 FETCH JOIN + 나머지는 @BatchSize, 또는 쿼리 분리.

---

### 검색

**Q: 검색 기능에 Elasticsearch를 안 쓴 이유는?**
A: 필터가 5개(keyword/district/category/minPrice/maxPrice) 수준이고 전문 검색(형태소 분석, 오타 보정)이 필요하지 않아 JPA Specification으로 충분했습니다. Elasticsearch는 별도 인프라 운영 비용과 데이터 동기화 복잡성이 추가되어 현재 규모에서는 과도했습니다.

- 꼬리질문: "사용자 수가 10배 늘어나면 어떻게 할 건가요?"
  → 답변 방향: 먼저 쿼리 최적화(인덱스 튜닝, 커버링 인덱스). 그 다음 Redis 캐시(검색 결과 캐싱). 그 이후 Elasticsearch 도입 고려.
  → 관련 개념: **커버링 인덱스(Covering Index)** — SELECT 컬럼이 인덱스에 모두 포함되어 테이블 접근 없이 인덱스만으로 쿼리 완료. 조회 성능 극적 향상.

---

## README 작성 가이드

사용자가 README를 요청하면 다음 섹션 포함:

1. **프로젝트 소개** (한 줄 설명)
2. **주요 기능** (기술 포인트 포함)
3. **기술 스택** (표 형식)
4. **아키텍처** (Redis 키 패턴 포함)
5. **실행 방법** (로컬 세팅 — Redis 선행 필수 명시)
6. **구현 포인트** (기술적 도전 3개)

---

---

## AI 에이전트 워크플로우 설계 (차별화 포인트)

### 한 줄 설명
"AI를 사용한 게 아니라, 실제 소프트웨어 팀 구조를 AI 에이전트로 설계하고 운영했습니다."

### 구조

```
기능 요청 (팀장 = 나)
    ↓
[planner-agent]  코드 탐색 → 구현 계획 수립    (코드 수정 권한 없음)
    ↓
[backend-agent]  git worktree 격리 환경에서 구현 → 빌드 검증 필수
    ↓
[test-agent]     테스트 작성 및 실행             (src/main/ 수정 권한 없음)
    ↓
[review-agent]   보안/성능/컨벤션 코드리뷰       (코드 수정 권한 없음)
    ↓
전체 테스트 통과 + review PASS → main 병합
```

### 면접 답변 뼈대

"단순히 AI 코파일럿을 쓴 게 아니라, 실제 팀처럼 역할이 분리된 에이전트 시스템을 직접 설계했습니다.

예를 들어 backend-agent는 항상 git worktree 격리 환경에서만 작업하게 강제해서 main 브랜치를 오염시키지 못하게 했고, test-agent는 구현 코드(src/main/)를 수정할 권한이 없어서 '테스트를 통과시키려고 구현 코드를 바꾸는' 문제를 구조적으로 차단했습니다.

병합 조건도 시스템으로 강제했는데 — 전체 테스트 통과 + review PASS — 이걸 지키지 않으면 merge하지 않는 규칙입니다."

### 꼬리질문 & 답변

**Q: AI가 코드를 다 짠 거 아닌가요? 직접 구현한 게 맞나요?**
→ "에이전트가 구현하고 제가 설계 검토, 코드 리뷰, 병합 판단을 했습니다. 더 중요한 건 — 어떤 제약을 걸지, 어떤 품질 게이트를 만들지, 에이전트간 역할 경계를 어떻게 설계할지는 전부 제가 결정했습니다. 실제 팀 리드가 팀원에게 일을 위임하는 것과 같습니다."

**Q: 에이전트가 만든 코드의 품질을 어떻게 보장했나요?**
→ "세 단계 품질 게이트를 설계했습니다. (1) 빌드 성공 필수 — backend-agent가 빌드 실패 시 스스로 최대 3회 자가복구하도록 설계, (2) 테스트 통과 — test-agent가 독립적으로 작성한 테스트 전부 통과, (3) review-agent의 보안/성능/컨벤션 리뷰 PASS. 이 세 가지를 모두 통과해야만 main에 병합합니다."

**Q: 에이전트 설계에서 가장 어려웠던 점은?**
→ "플래너가 코드를 직접 고치려는 경향, 백엔드 에이전트가 main 브랜치에서 작업하려는 경향 — 이런 '역할 이탈'을 프롬프트와 도구 접근 제한으로 방어하는 게 어려웠습니다. 각 에이전트의 도구 권한을 명시적으로 제한해서 해결했습니다 (planner는 Read/Grep만, backend는 Edit/Write/Bash 포함)."

### README에 넣을 섹션 (요청 시 작성)

```markdown
## 개발 방법론

역할이 분리된 멀티 에이전트 워크플로우로 개발했습니다.

| 에이전트 | 역할 | 제약 |
|---|---|---|
| planner | 설계·태스크 분해 | 코드 수정 불가 |
| backend | 풀스택 구현 | git worktree 격리 필수 |
| test | 테스트 작성·실행 | src/main/ 수정 불가 |
| review | 코드리뷰 | 수정 불가, 피드백만 |

병합 조건: `./gradlew test` 전체 통과 + review PASS
```

---

## 사용 지침

- "기술적으로 말해줘" → 코드 레벨로 구체화
- "쉽게 말해줘" → 개념 중심으로 전환
- 특정 기능만 묻는 경우 해당 섹션만 참고
- 수치 없는 성과 주장("성능 향상") 금지 — 근거 있는 것만 사용
- 꼬리질문 출력 시 관련 개념은 반드시 한 줄 정의 포함
