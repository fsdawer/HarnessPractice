---
name: feature-implementation
description: 새 기능 구현 시 단계별 워크플로우를 안내합니다. "~기능 만들어줘", "~추가해줘", "~구현해줘" 요청에 사용합니다. 구현 전 반드시 이 스킬을 따르세요.
---

# Feature Implementation Workflow — CutIng

구현 시작 전 **모든 단계를 순서대로** 완료해야 합니다. 단계를 건너뛰면 안 됩니다.

---

## 0단계: 에이전트 호출 순서 (건너뛰기 금지)

이 스킬이 호출되면 아래 순서를 따른다.

```
① planner-agent 투입
   → 설계 계획 + 트레이드오프 사용자에게 제시
   → 사용자 승인 대기

② 승인 후 worktree 생성
   git worktree add ../beauty-feature-<기능명> -b feature/<기능명>

③-A backend-agent 투입 (worktree에서)
   → 엔티티/레포지토리/서비스/DTO/컨트롤러 구현
   → ./gradlew build -x test PASS 확인 후 완료 보고

③-B frontend-agent 투입 (worktree에서, ③-A 완료 후)
   → API 파일 / 뷰 / 컴포넌트 구현
   → npm run build PASS 확인 후 완료 보고

   ※ planner가 API 스펙을 상세히 정의했다면 ③-A 와 ③-B 병렬 실행 가능

④ test-agent 투입
   → 단위·통합 테스트 작성 및 실행

⑤ review-agent 투입
   → 보안·성능·컨벤션 리뷰

⑥ gradlew test PASS + review PASS 확인 후
   git merge --no-ff → worktree 삭제
```

---

## 1단계: 요구사항 분석

구현 전 명확히 해야 할 것들:

- **입력**: 누가 어떤 데이터를 보내는가? (userId, requestBody 구조)
- **출력**: 무엇을 반환하는가? (ResponseEntity 구조)
- **권한**: 인증 필요 여부, ROLE 제한 여부 (`@LoginUserId` 사용 여부)
- **상태 변화**: DB에 무엇이 바뀌는가?
- **비동기 여부**: 알림, 이메일, 이벤트 발행이 있는가?

불명확한 항목이 있으면 구현 전에 질문합니다.

---

## 2단계: 영향 범위 확인

코드를 읽어서 확인합니다. 추측하지 않습니다.

```
확인 대상:
- 관련 Entity: 어떤 테이블이 변경/조회되는가
- 관련 도메인: 어떤 도메인 패키지가 영향받는가
- Redis 키: 기존 캐시/락 키 패턴과 충돌하는가 (CLAUDE.md Redis 키 패턴 확인)
- 기존 API: 변경이 기존 엔드포인트 동작을 바꾸는가
```

---

## 3단계: Entity 확인 및 필요 시 수정

기존 Entity를 먼저 읽습니다.

**수정 시 필수 체크:**
- `@Builder.Default` — 기본값 있는 필드(`= LocalDateTime.now()`, `= 0` 등)에 반드시 붙임
- `@SQLRestriction("deleted_at IS NULL")` — 소프트 딜리트 대상이면 추가
- 연관관계: `fetch = FetchType.LAZY` 기본, 필요할 때만 EAGER
- Jackson: `tools.jackson.*` 사용, `com.fasterxml` 절대 금지

새 Entity 필요 시:
```java
@Entity
@Table(name = "table_name", indexes = {
    @Index(name = "idx_...", columnList = "...")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NewEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default  // 기본값 있는 필드는 반드시
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

---

## 4단계: DTO 생성

**Request DTO:**
- `@Valid` + `@NotNull`, `@NotBlank` 등으로 입력 검증
- 빌더 패턴 사용

**Response DTO:**
- `static from(Entity entity)` 팩토리 메서드 포함
- 프론트가 실제로 쓰는 필드만 포함 (불필요한 내부 필드 노출 금지)

**Redis 저장용 DTO (필요 시):**
- `@Setter` 필수 (Jackson 역직렬화 요건)
- `@NoArgsConstructor` 필수

---

## 5단계: Repository → Service → Controller 순서로 구현

### Repository
- JPQL에서 N+1 발생 여부 확인
- 컬렉션 조회 시 `JOIN FETCH` 또는 `@EntityGraph` 적용
- 1:N 조인 있으면 `DISTINCT` 또는 `EXISTS 서브쿼리` 사용

### Service
```
구현 순서:
1. interface에 메서드 시그니처 추가
2. ServiceImpl에 구현

체크리스트:
- @Transactional 범위: 읽기 전용은 @Transactional(readOnly=true)
- 예외 처리: throw new CustomException(ErrorCode.XXX) 패턴
- 분산락 필요 여부: 동시 요청 가능한 자원 변경이면 Redis 락 적용
- 알림 필요 여부: notificationService.notify...() 비동기 호출
```

### Controller
```java
@RestController
@RequestMapping("/api/...")
@RequiredArgsConstructor
public class XxxController {

    @GetMapping
    public ResponseEntity<XxxResponse> get(@LoginUserId Long userId) { ... }

    @PostMapping
    public ResponseEntity<XxxResponse> create(
            @LoginUserId Long userId,
            @Valid @RequestBody XxxRequest request) { ... }
}
```

---

## 6단계: 프론트엔드 연결 (필수)

백엔드 API 완성 후 반드시 아래 두 가지를 구현한다. 건너뛰면 완성이 아니다.

### API 파일 (`frontend/src/api/`)
```js
// 예시: frontend/src/api/reservation.js
export const reservationApi = {
  create: (data) => api.post('/api/reservations', data),
  getMyList: () => api.get('/api/reservations/me'),
}
```

### 화면 (`frontend/src/views/` 또는 `components/`)
- 해당 기능을 실제로 사용할 수 있는 Vue 컴포넌트 구현
- 로딩 상태(`loading`), 에러 상태(`error`) 처리 포함
- 라우터(`frontend/src/router/index.js`)에 경로 추가 필요 시 추가
- Navbar에 진입점이 없으면 추가

**체크:**
- [ ] `api.post/get` 호출이 실제 백엔드 엔드포인트와 일치하는가
- [ ] 백엔드·프론트 동시에 실행해서 기능이 실제로 동작하는가

---

## 7단계: 테스트 작성

최소한 아래 케이스를 커버합니다:

```
정상 케이스: 입력이 올바를 때 기대 출력 반환
예외 케이스: 존재하지 않는 ID, 권한 없음, 중복 요청
경계 케이스: 빈 목록 조회, 동시 요청 (분산락 있는 경우)
```

통합 테스트 우선 (`@SpringBootTest`), 단위 테스트 필요 시 Mockito 사용.

---

## 7단계: 리뷰 체크 (self-review)

커밋 전 스스로 확인:

| 항목 | 확인 |
|---|---|
| `com.fasterxml` import 없음 | ☐ |
| `@Builder.Default` 누락 없음 | ☐ |
| `@Transactional` 범위 적절 | ☐ |
| N+1 없음 | ☐ |
| CustomException 사용 (RuntimeException 직접 던지기 금지) | ☐ |
| Redis 키 패턴 CLAUDE.md와 일치 | ☐ |
| 테스트 작성 완료 | ☐ |
| 프론트 API 파일 업데이트 완료 | ☐ |
