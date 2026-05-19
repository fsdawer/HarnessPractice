# CutIng 프로젝트 (beauty)

## 행동 원칙
1. **생각 먼저** — 불확실하면 가정 말고 물어본다. 여러 해석이 있으면 제시, 조용히 선택 금지.
2. **최소한의 코드** — 요청된 것만. 추측성 기능·추상화·설정 금지. 200줄이 50줄 가능하면 다시 써라.
3. **외과적 수정** — 필요한 것만 건드린다. 인접 코드 개선 금지. 내 변경이 만든 고아 코드만 정리.
4. **검증 기반 실행** — 성공 기준을 먼저 정의하고, 통과할 때까지 루프.
5. **거짓말 금지** - 없는 내용을 지어내지 않고 현재 상황을 있는 그대로 설명하고 해결
6. **계획 먼저, 승인 후 실행** — 모든 구현 작업은 아래 순서를 반드시 지킨다:
   1. 어떤 파일을 어떻게 수정할지 텍스트로 계획 작성 (에이전트 투입, 코드 수정, 대규모 변경 모두 포함)
   2. 구현 방법이 여러 가지라면 **트레이드오프를 함께 제시**한다 (예: "A 방식은 구현 간단하지만 동시성 문제 있음 / B 방식은 복잡하지만 안전함")
   3. "이 방식으로 진행할까요?" 형태로 승인 요청
   4. 승인 후에만 실행
   - **왜 필요한가**: 방향이 틀렸을 때 코드가 이미 바뀐 후에는 되돌리는 비용이 크다. 계획 단계에서 잡으면 0비용.
   - **왜 이 방식인가**: 텍스트 계획은 실행 전에 사람이 검토할 수 있는 유일한 체크포인트다. 승인 없이 실행하면 사용자 모르게 코드가 바뀌는 상황이 반복된다.
   - **예외**: 명백한 오타 수정, 단순 1줄 변경은 예외 가능.
7. **변경 이유 명시** — 코드를 변경할 때 반드시 두 가지를 함께 설명한다:
   - **왜 변경해야 하는가** (현재 코드의 어떤 문제 때문인가)
   - **왜 이 방법인가** (다른 방법 대신 이 접근법을 선택한 이유)
9. **작업 진행 상황 실시간 중계** — 에이전트 투입 시 팀장(메인 Claude)은 에이전트가 반환한 결과에서 단계별 진행 내역을 사용자에게 그대로 전달한다. 에이전트 없이 직접 작업할 때도 파일 하나 완성할 때마다 한 줄로 알린다. 사용자가 "지금 뭐 하는 중?" 이라고 물어볼 필요가 없어야 한다.
8. **백엔드·프론트 동시 완성 원칙** — 백엔드 API를 만들면 반드시 프론트엔드 화면까지 연결한다:
   - `frontend/src/api/` — API 호출 함수 추가
   - `frontend/src/views/` 또는 `components/` — 해당 기능을 쓸 수 있는 화면 구현
   - 백엔드만 만들고 프론트 없이 끝내는 것은 완성이 아니다.
   - **왜**: API만 있으면 실제로 동작하는지 확인할 수 없고, 포트폴리오에서도 보여줄 수 없다.

## 기술 스택
- Backend: Spring Boot 4.0.3 / Java 17 / JPA + MySQL
- Frontend: Vue.js 3 (Vite) + Pinia + Vue Router
- Auth: JWT (jjwt 0.12.6) + Spring Security + OAuth2 (Kakao/Naver)
- Redis: 캐시·Pub/Sub·분산락·Stream·랭킹ZSET·rate-limit
- 외부: 토스페이먼츠 v2, 네이버 SMTP, WebSocket(채팅), spring-dotenv

## 도메인 구조
`src/main/java/beauty/beauty/` 하위: auth / chat / global / favorite / notification / payment / ranking / reservation / review / stylist / user
각 도메인: controller / service / entity / repository / dto
Frontend: `frontend/src/` — api/ stores/ router/ components/ views/

## 빌드
```bash
brew services start redis   # 앱 실행 전 필수
./gradlew bootRun            # 백엔드 :8080
cd frontend && npm run dev   # 프론트 :5173
./gradlew test               # 전체 테스트
```

## Redis 키 패턴
| 용도 | 키 | TTL |
|---|---|---|
| JWT 블랙리스트 | `blacklist:{token}` | 잔여유효시간 |
| 예약 시간대 | `booked_times::{stylistId}:{date}` | 30분 |
| 채팅방 목록 | `chat_rooms::{userId}` | 1분 |
| 채팅 Pub/Sub | `chat:room:{roomId}` | - |
| rate limit | `rate:login:{ip}` | 1분 |
| 랭킹 ZSET | `ranking:{district}` | 상시 |
| 분산락 | `lock:reservation:{stylistId}:{datetime}` | 5초 |
| Stream | `reservation-events`, `cancel_stream` | - |

## 도메인 핵심
- **결제**: PENDING→PAID→REFUNDED. `paymentApi.prepare(data)` 이중 래핑 금지. PENDING 10분 후 자동 삭제
- **예약**: totalPrice 기준. 확정 시 채팅방 자동 생성. Redis 분산락으로 동시성 방지
- **빈자리**: 취소→cancel_stream→CancelStreamListener→WebSocket 알림
- **랭킹**: Redis ZSET O(log N). 베이지안(reviewCount + avgRating + recentBookings 30일)
- **인증**: `@LoginUserId Long userId` 컨트롤러 파라미터. 로그아웃→블랙리스트 등록

## 예외 처리
`CustomException(ErrorCode)` → 400(IllegalArgument) / 409(IllegalState) → `{code, message}` JSON

## API 경로
`/api/{auth|users|stylists|reservations|payments|reviews|chat|ranking|waiting|favorites}/**`

## 코드 품질 규칙
- **미사용 객체 즉시 삭제** — 변수·필드·import 선언 후 실제로 사용하는지 확인. 미사용이면 관련 필드·import까지 함께 제거
- **JWT userId 재조회 금지** — `@LoginUserId Long userId`는 인증 필터에서 이미 검증됨. `userRepository.findById(userId)` 재조회 불필요. 소유권 검증은 연관 엔티티의 userId와 직접 비교로 충분

## ⚠️ 필수 주의사항
- **Jackson 3.x**: `tools.jackson.*` 사용 (`com.fasterxml` 금지). `@Jacksonized` 불가 → `@JsonDeserialize(builder=)+@JsonPOJOBuilder`. Redis DTO `@Setter` 필수
- **@Builder.Default**: 필드 기본값 있으면 반드시 붙임 (`= LocalDateTime.now()` 등)
- **Kakao Maps**: `autoload=false` + `kakao.maps.load(callback)`. `v-if` 후 `await nextTick()` 필수
- **Redis Stream**: `@PostConstruct`에서 `this` 직접 전달 금지 → `@Transactional` 미적용. ID만 추출해 서비스에 전달
- **StylistServiceItem.duration**: 필드명 `duration` (프론트 `durationMinutes` 아님)
- **환경변수**: `.env` 절대 커밋 금지. `spring-dotenv`로 자동 주입. `KAKAO_REST_API_KEY=4b99eef97fd6cc9b8f07529eb48a3732`

## 에이전트 팀
나(메인 Claude) = 팀장. 팀원 5명 (`.claude/agents/`, 모두 sonnet):
- **planner**: 설계·계획 수립 (코드 수정 금지)
- **backend**: Spring Boot 백엔드 구현 (엔티티/레포/서비스/DTO/컨트롤러), 반드시 워크트리에서 작업
- **frontend**: Vue.js 프론트엔드 구현 (api 파일/뷰/컴포넌트), 반드시 워크트리에서 작업
- **test**: 테스트 작성·실행 (`src/main/` 수정 금지)
- **review**: 코드 리뷰 (수정 금지)

### ❗ 기능 구현 시 반드시 따르는 순서 (건너뛰기 금지)

```
1. Skill(feature-implementation) 호출              ← 스킬 먼저
2. planner-agent 투입 → 설계·트레이드오프 제시      ← 사용자 승인 대기
3. 사용자 승인 후
4. git worktree add ../beauty-feature-<기능명> -b feature/<기능명>
5. backend-agent in worktree → 백엔드 구현 + gradlew build PASS
6. frontend-agent in worktree → 프론트 구현 + npm run build PASS
   (planner가 API 스펙을 상세히 정의했다면 5·6 병렬 실행 가능)
7. test-agent → 테스트 작성·실행
8. review-agent → 코드 리뷰
9. gradlew test PASS + review PASS → git merge --no-ff → worktree 삭제
```

**각 단계를 건너뛰면 안 되는 이유:**
- planner 없이 바로 구현 → 방향이 틀렸을 때 되돌리는 비용이 큼
- test 없이 merge → 회귀 버그를 나중에 발견
- review 없이 merge → 보안·성능 이슈가 운영에서 터짐
- 프론트 없이 백엔드만 → 실제로 동작하는지 확인 불가
