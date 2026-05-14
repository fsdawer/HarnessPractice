---
name: code-review
description: 코드 리뷰 시 사용합니다. "리뷰해줘", "코드 확인해줘", "문제 없어?", "머지해도 돼?" 요청에 사용합니다. 변경된 코드를 보여주거나 파일 경로를 알려주면 검토합니다.
---

# Code Review Checklist — CutIng

코드를 수정하지 않습니다. 문제를 발견하면 **어디에 무엇이 문제인지** 명시합니다.

심각도 기준:
- 🔴 **BLOCK** — 머지 불가. 버그, 보안, 데이터 손실 가능성
- 🟡 **WARN** — 머지 가능하나 수정 권장. 성능, 일관성 문제
- 🟢 **INFO** — 선택적 개선. 가독성, 컨벤션

---

## 1. N+1 쿼리 검사 🔴

```
확인 대상:
- 루프 안에서 repository 메서드 호출
- @ManyToOne, @OneToMany를 LAZY 로딩 후 반복 접근
- .stream().map()에서 연관 엔티티 접근

해결 패턴:
- JOIN FETCH / @EntityGraph
- 1:N 조인 있으면 DISTINCT 또는 EXISTS 서브쿼리
- IN 쿼리로 일괄 조회 후 Map으로 매핑
```

---

## 2. @Transactional 범위 검사 🔴

```
확인 항목:
- 여러 DB 작업이 하나의 트랜잭션으로 묶여 있는가?
- 읽기 전용 메서드에 @Transactional(readOnly=true) 사용하는가?
- @Transactional 메서드 내에서 지연 로딩하는가?
- private 메서드에 @Transactional 붙어있지 않은가? (프록시 미적용)
- @Async 메서드에 @Transactional 있는가? (별도 스레드라 롤백 안 됨)

Redis Stream @PostConstruct 주의:
- @PostConstruct에서 this 직접 전달 금지 → @Transactional 미적용
```

---

## 3. 분산락 / 동시성 검사 🔴

```
분산락 필요 케이스:
- 같은 자원을 동시에 여러 요청이 수정 가능한 경우
  예: 예약(stylistId + dateTime), 재고 차감, 포인트 차감

확인 항목:
- 락 획득 후 반드시 finally에서 해제하는가?
- TTL이 작업 소요 시간보다 충분히 긴가? (현재 5초)
- 락 획득 실패 시 CustomException 던지는가? (무한 대기 금지)
```

---

## 4. Redis TTL / 키 패턴 검사 🟡

```
CLAUDE.md Redis 키 패턴과 대조:
- 새 키를 추가했으면 CLAUDE.md 패턴 목록에 문서화되어 있는가?
- TTL 없는 키는 의도된 것인가? (랭킹 ZSET 제외)
- 키 이름이 기존 패턴과 일관성이 있는가?

흔한 실수:
- TTL 설정 없이 영구 저장 (메모리 누수)
- 환경별 키 충돌 (dev/prod 구분 없음)
```

---

## 5. 예외 처리 검사 🔴

```
금지 패턴:
- throw new RuntimeException("메시지")  → CustomException(ErrorCode) 사용
- catch (Exception e) {}  → 예외 삼킴 금지
- e.printStackTrace()  → log.error() 사용
- 500 반환해야 할 것을 200으로 감싸기

확인 항목:
- EntityNotFoundException → 어떤 상태코드로 응답하는가?
- 클라이언트 잘못(400) vs 서버 내부 오류(500) 구분되어 있는가?
```

---

## 6. Jackson / 직렬화 검사 🔴

```
금지:
- import com.fasterxml.jackson.*  →  tools.jackson.* 만 사용
- @Jacksonized 사용 (Jackson 3.x 미지원)

Redis 저장 DTO 필수:
- @Setter 있는가?
- @NoArgsConstructor 있는가?

Builder 패턴:
- 기본값 있는 필드에 @Builder.Default 붙어있는가?
```

---

## 7. 보안 검사 🔴

```
확인 항목:
- 다른 사용자의 자원에 접근 가능한가?
  예: /api/reservations/{id} → 본인 예약인지 확인하는가?
- @LoginUserId로 인증된 userId를 사용하는가? (RequestParam으로 받지 않음)
- 민감 정보(비밀번호, 토큰)가 Response DTO에 포함되지 않는가?
- .env 파일이 커밋되지 않았는가?
```

---

## 8. 테스트 검사 🟡

```
확인 항목:
- 새 기능에 대한 테스트가 있는가?
- 정상 케이스만 있고 예외 케이스가 없지 않은가?
- H2 호환 쿼리인가? (native query 사용 시 특히 확인)
- 테스트가 DB/Redis 상태에 의존하는가? (@Transactional 롤백 확인)
```

---

## 9. 성능 검사 🟡

```
확인 항목:
- 대용량 조회에 페이지네이션이 없는가?
- Kafka/Redis 없이 루프 안에서 외부 API 호출하는가?
- 불필요한 전체 조회 후 메모리에서 필터링하는가?
```

---

## 리뷰 결과 출력 형식

```
## 코드 리뷰 결과

### 🔴 BLOCK (N건)
1. [파일:라인] 문제 설명 → 수정 방향

### 🟡 WARN (N건)
1. [파일:라인] 문제 설명 → 수정 방향

### 🟢 INFO (N건)
1. [파일:라인] 개선 제안

### 결론
BLOCK 없음 → 머지 가능 / BLOCK 있음 → 수정 필요
```
