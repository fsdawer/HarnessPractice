---
name: explain-code
description: 코드를 도메인 맥락과 실제 데이터 흐름으로 설명합니다. "어떻게 동작해?", "이 코드 뭐야?", "설명해줘", "플로우 알려줘" 처럼 코드 이해를 묻는 질문에 사용합니다.
---

코드를 설명할 때 다음 순서를 따르세요.

## 1. 한 줄 역할 요약

코드가 시스템에서 담당하는 역할을 한 문장으로 먼저 말합니다.
- "예약 생성 요청을 받아 Redis 분산락으로 중복을 막고 DB에 저장하는 서비스 메서드"
- "스타일리스트 목록을 district/category/price 조건으로 필터링해 반환하는 JPA Specification"

추상적 비유 금지. 이 프로젝트의 실제 도메인 언어(예약, 스타일리스트, 결제, 채팅 등)로 표현합니다.

## 2. 입력 → 처리 → 출력 플로우

코드가 어떤 값을 받아서 어떤 메서드를 거쳐 무엇을 반환하는지 추적합니다.

**형식:**
```
입력: ReservationRequest(stylistId=3, dateTime="2025-06-01T14:00", serviceId=7)
  ↓
1. stylistProfileRepository.findById(3) → StylistProfile 로드
2. Redis SETNX "lock:reservation:3:2025-06-01T14:00" → 락 획득 여부 확인
3. [락 실패] → CustomException(RESERVATION_CONFLICT) 던지고 종료
4. [락 성공] → reservationRepository.save(reservation) → Reservation 엔티티 저장
5. notificationService.notifyReservationCreated(reservation) → SSE 비동기 알림
6. Redis DEL 락 해제
  ↓
출력: ReservationResponse(id=42, status=CONFIRMED, ...)
```

라인 번호 참조가 도움이 될 때: `line 58: findByUserId로 StylistProfile 조회`

분기가 있으면 `[조건A]` / `[조건B]`로 명시합니다.

## 3. ASCII 다이어그램 (복잡도에 따라 선택)

단순한 코드는 생략. 여러 레이어나 컴포넌트가 얽힌 경우에만 그립니다.

계층 구조:
```
StylistProfile
├── User (1:1) — 이름, 이메일, 권한
├── Salon (N:1) — 위치, district
└── StylistServiceItem[] (1:N) — 커트/펌/염색, 가격
```

시퀀스 (동시성, 비동기):
```
Client      Controller    Service      Redis       DB
  │──POST──→   │             │            │          │
              │──createRes→  │            │          │
                             │──SETNX──→  │          │
                             │←─OK────    │          │
                             │──save───────────────→  │
                             │──DEL────→  │          │
              │←──Response── │            │          │
```

## 4. 핵심 포인트 & 주의사항

이 코드에서 놓치면 안 되는 것 1~3개만 짚습니다.

- ⚠️ `@Transactional` 누락 시 락 해제 전 예외 발생하면 락이 영구적으로 남음
- ⚠️ `query.distinct(true)` 없으면 1:N join에서 StylistProfile이 중복 조회됨
- ✅ `@EntityGraph({"user","salon"})` — findAll(spec, sort) 호출 시 N+1 방지

---

**길이 조절**: 간단한 코드(getter, 단순 조회)는 1~2번만으로 충분합니다. 복잡한 플로우(분산락, 스트림 처리, Specification)일수록 2번 플로우 추적에 집중하세요. 모든 섹션을 억지로 채우지 마세요.
