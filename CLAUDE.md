# CutIng 프로젝트 (beauty)

## 행동 원칙
1. **생각 먼저** — 불확실하면 가정 말고 물어본다. 여러 해석이 있으면 제시, 조용히 선택 금지.
2. **최소한의 코드** — 요청된 것만. 추측성 기능·추상화·설정 금지. 200줄이 50줄 가능하면 다시 써라.
3. **외과적 수정** — 필요한 것만 건드린다. 인접 코드 개선 금지. 내 변경이 만든 고아 코드만 정리.
4. **검증 기반 실행** — 성공 기준을 먼저 정의하고, 통과할 때까지 루프.
5. **거짓말 금지** - 없는 내용을 지어내지 않고 현재 상황을 있는 그대로 설명하고 해결

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

## ⚠️ 필수 주의사항
- **Jackson 3.x**: `tools.jackson.*` 사용 (`com.fasterxml` 금지). `@Jacksonized` 불가 → `@JsonDeserialize(builder=)+@JsonPOJOBuilder`. Redis DTO `@Setter` 필수
- **@Builder.Default**: 필드 기본값 있으면 반드시 붙임 (`= LocalDateTime.now()` 등)
- **Kakao Maps**: `autoload=false` + `kakao.maps.load(callback)`. `v-if` 후 `await nextTick()` 필수
- **Redis Stream**: `@PostConstruct`에서 `this` 직접 전달 금지 → `@Transactional` 미적용. ID만 추출해 서비스에 전달
- **StylistServiceItem.duration**: 필드명 `duration` (프론트 `durationMinutes` 아님)
- **환경변수**: `.env` 절대 커밋 금지. `spring-dotenv`로 자동 주입. `KAKAO_REST_API_KEY=4b99eef97fd6cc9b8f07529eb48a3732`

## 에이전트 팀
나(메인 Claude) = 팀장. 팀원 4명 (`.claude/agents/`, 모두 sonnet):
- **planner**: 설계·계획 수립 (코드 수정 금지)
- **backend**: Spring Boot + Vue.js 구현, 반드시 워크트리에서 작업
- **test**: 테스트 작성·실행 (`src/main/` 수정 금지)
- **review**: 코드 리뷰 (수정 금지)

워크트리: `git worktree add ../beauty-feature-<기능명> -b feature/<기능명>`
병합 조건: `./gradlew test` 전체 통과 + review PASS → `git merge --no-ff` → worktree 삭제
