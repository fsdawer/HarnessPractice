---
name: db-access-guard
description: DB 접근 패턴의 안전성을 검사합니다. "DB 접근 괜찮아?", "레포지토리 직접 써도 돼?", "쿼리 검토해줘", "DB 설계 맞아?" 요청에 사용합니다. 코드를 수정하지 않고 위반 사항만 보고합니다.
---

# DB Access Guard — CutIng

이 프로젝트의 DB 접근 규칙을 검사합니다. **코드를 수정하지 않습니다.**

---

## 핵심 규칙

### 규칙 1: Controller는 Repository를 직접 호출하지 않는다 🔴

```
금지:
Controller → Repository (직접)

허용:
Controller → Service → Repository
```

Controller에 `@Autowired` 또는 생성자로 Repository가 주입되어 있으면 BLOCK.

---

### 규칙 2: 모든 쓰기 작업은 @Transactional 안에 있어야 한다 🔴

```
확인 대상:
- repository.save(), delete(), @Modifying @Query 호출
- 위 작업이 있는 Service 메서드에 @Transactional 없으면 BLOCK

예외:
- @Transactional이 호출하는 상위 메서드에 있는 경우 OK
```

---

### 규칙 3: 전체 조회 후 메모리 필터링 금지 🟡

```
금지 패턴:
repository.findAll()
  .stream()
  .filter(e -> e.getDistrict().equals(district))

이유: 데이터 증가 시 OOM 및 성능 급격한 저하

허용:
@Query("SELECT s FROM Stylist s WHERE s.district = :district")
List<Stylist> findByDistrict(@Param("district") String district);
```

---

### 규칙 4: N+1을 유발하는 루프 접근 금지 🔴

```
금지 패턴:
List<Reservation> reservations = reservationRepository.findAll();
for (Reservation r : reservations) {
    r.getStylistProfile().getUser().getName();  // 매번 쿼리 발생
}

허용:
@Query("SELECT r FROM Reservation r JOIN FETCH r.stylistProfile sp JOIN FETCH sp.user")
List<Reservation> findAllWithStylist();
```

---

### 규칙 5: Native Query 사용 시 정당성이 있어야 한다 🟡

```
Native Query 허용 케이스:
- 공간 쿼리 (ST_Distance_Sphere, ST_GeomFromText)
- FULLTEXT 검색 (MATCH AGAINST)
- MySQL 전용 기능 (FOR UPDATE SKIP LOCKED 등)

Native Query 금지 케이스:
- 단순 CRUD → JPQL 또는 Spring Data 메서드 사용
- Native Query인데 JPQL로 동일하게 표현 가능한 경우
```

---

### 규칙 6: 대량 조회에 페이지네이션이 없으면 안 된다 🟡

```
확인 대상:
- 사용자가 임의로 늘어날 수 있는 테이블의 전체 조회
  (reservations, reviews, stylists 등)

허용:
Pageable pageable = PageRequest.of(page, size);
repository.findAll(pageable);

또는 No-Offset 커서 페이지네이션 (이미 적용된 곳 있음)
```

---

### 규칙 7: 동시 요청 가능 자원에 락이 없으면 안 된다 🔴

```
락 필요 케이스 (이 프로젝트 기준):
- 예약 생성: stylistId + dateTime 조합
- 포인트/잔액 차감
- 한정 수량 자원

확인 방법:
Service 메서드에서 조회 → 조건 체크 → 저장 패턴이 있으면
두 요청이 동시에 조회 시점에 통과할 수 있는지 검토
```

---

### 규칙 8: @SQLRestriction 적용 엔티티의 소프트 딜리트 우회 금지 🔴

```
이 프로젝트의 소프트 딜리트 엔티티:
- StylistProfile (@SQLRestriction("deleted_at IS NULL"))

금지:
native query에서 deleted_at 조건 없이 stylist_profiles 직접 조회
→ 삭제된 미용사가 노출될 수 있음

확인:
native query 사용 시 WHERE deleted_at IS NULL 포함 여부
```

---

## 보고 형식

```
## DB 접근 가드 결과

### 🔴 위반 (N건)
1. [파일:라인] 규칙 N 위반 — 구체적인 문제
   → 수정 방향

### 🟡 주의 (N건)
1. [파일:라인] 구체적인 내용
   → 수정 방향

### ✅ 통과
이상 없음
```
