

# CutIng 프로젝트 (beauty)

## 기술 스택
- **Backend**: Spring Boot 4.0.3 / Java 17 / JPA + MySQL
- **Frontend**: Vue.js 3 (Vite) + Pinia + Vue Router
- **Auth**: JWT (`io.jsonwebtoken:jjwt 0.12.6`) + Spring Security + OAuth2 (Kakao/Naver)
- **Cache/Pub-Sub**: Redis (Spring Data Redis 4.0.3 + Spring Cache)
- **외부 API**: 토스페이먼츠 v2, 네이버 SMTP
- **기타**: WebSocket(채팅), spring-dotenv(환경변수), Lombok

## 프로젝트 폴더 구조

```
beauty/
├── CLAUDE.md
├── README.md
├── build.gradle
├── settings.gradle
├── gradlew / gradlew.bat
├── .env                          # 절대 커밋 금지 (.gitignore 처리됨)
├── sql/                          # DDL/초기 데이터 SQL 스크립트
├── k6/                           # 부하 테스트 스크립트
│   ├── test_stylist_detail.js    # 스타일리스트 상세 조회 (before/after 캐시)
│   ├── test_stylist_cache.js     # 스타일리스트 목록+상세 캐시 효과 측정
│   ├── test_distributed_lock.js  # 동시 예약 race condition (before/after 분산락)
│   └── test_image_upload.js      # 이미지 업로드 동시성 테스트
│
├── src/
│   ├── main/
│   │   ├── java/beauty/beauty/
│   │   │   ├── BeautyApplication.java
│   │   │   ├── auth/
│   │   │   │   ├── controller/AuthController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── AuthService.java
│   │   │   │   │   ├── AuthServiceImpl.java        # logout 시 access token 블랙리스트 등록
│   │   │   │   │   └── CustomOAuth2UserService.java
│   │   │   │   └── dto/
│   │   │   │       ├── RegisterRequest.java
│   │   │   │       ├── LoginRequest.java
│   │   │   │       └── TokenResponse.java
│   │   │   ├── chat/
│   │   │   │   ├── controller/ChatController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── ChatService.java
│   │   │   │   │   └── ChatServiceImpl.java        # sendMessage → Redis Pub/Sub, getMyRooms @Cacheable
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ChatRoomResponse.java
│   │   │   │   │   ├── MessageResponse.java        # @JsonDeserialize(builder=) for Redis 역직렬화
│   │   │   │   │   └── SendMessageRequest.java
│   │   │   │   ├── entity/
│   │   │   │   │   ├── ChatRoom.java
│   │   │   │   │   └── Message.java
│   │   │   │   └── repository/
│   │   │   │       ├── ChatRoomRepository.java
│   │   │   │       └── MessageRepository.java
│   │   │   ├── global/
│   │   │   │   ├── annotation/LoginUserId.java       # 컨트롤러 파라미터 리졸버용
│   │   │   │   ├── config/
│   │   │   │   │   ├── RedisConfig.java              # CacheManager, RedisTemplate, MessageListenerContainer
│   │   │   │   │   ├── SecurityConfig.java
│   │   │   │   │   ├── WebConfig.java
│   │   │   │   │   └── WebSocketConfig.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── CustomException.java
│   │   │   │   │   ├── ErrorCode.java
│   │   │   │   │   └── GlobalExceptionHandler.java
│   │   │   │   ├── filter/
│   │   │   │   │   └── RateLimitFilter.java          # /api/auth/login IP당 1분 10회 제한
│   │   │   │   ├── jwt/
│   │   │   │   │   ├── JwtAuthFilter.java            # 블랙리스트 체크 포함
│   │   │   │   │   └── JwtUtil.java
│   │   │   │   ├── oauth2/OAuth2SuccessHandler.java
│   │   │   │   ├── redis/
│   │   │   │   │   ├── TokenBlacklistService.java    # Redis SET TTL로 access token 무효화
│   │   │   │   │   └── RedisMessageSubscriber.java   # chat:room:* 구독 → STOMP 브로드캐스트
│   │   │   │   └── resolver/LoginUserIdResolver.java
│   │   │   ├── favorite/
│   │   │   │   ├── controller/FavoriteController.java  # POST /api/favorites/stylists/{id}
│   │   │   │   ├── service/FavoriteService.java
│   │   │   │   ├── entity/FavoriteStylist.java
│   │   │   │   └── repository/FavoriteStylistRepository.java
│   │   │   ├── notification/
│   │   │   │   ├── service/NotificationService.java    # WebSocket 알림 발송 (@Async)
│   │   │   │   └── dto/NotificationMessage.java
│   │   │   ├── payment/
│   │   │   │   ├── controller/PaymentController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── PaymentService.java
│   │   │   │   │   ├── PaymentServiceImpl.java
│   │   │   │   │   └── PaymentCleanupScheduler.java  # PENDING 10분 후 자동 삭제
│   │   │   │   ├── entity/Payment.java               # PayStatus(PENDING→PAID→REFUNDED)
│   │   │   │   ├── repository/PaymentRepository.java
│   │   │   │   └── dto/
│   │   │   │       ├── PaymentPrepareRequest.java
│   │   │   │       ├── PaymentPrepareResponse.java
│   │   │   │       ├── PaymentConfirmRequest.java
│   │   │   │       ├── PaymentResponse.java
│   │   │   │       └── RefundRequest.java
│   │   │   ├── ranking/
│   │   │   │   ├── controller/RankingController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── RankingService.java
│   │   │   │   │   └── RankingServiceImpl.java       # Redis ZSET 베이지안 랭킹
│   │   │   │   └── dto/RankingResponse.java
│   │   │   ├── reservation/
│   │   │   │   ├── controller/
│   │   │   │   │   ├── ReservationController.java
│   │   │   │   │   └── WaitingController.java        # POST /api/waiting/stylists/{id}
│   │   │   │   ├── service/
│   │   │   │   │   ├── ReservationService.java
│   │   │   │   │   ├── ReservationServiceImpl.java   # Redis 분산락 + booked_times @Cacheable
│   │   │   │   │   └── WaitingService.java
│   │   │   │   ├── entity/
│   │   │   │   │   ├── Reservation.java              # status/createdAt에 @Builder.Default 적용
│   │   │   │   │   ├── ReservationImage.java
│   │   │   │   │   └── Waiting.java                  # 빈자리 대기 엔티티
│   │   │   │   ├── event/
│   │   │   │   │   ├── ReservationStreamListener.java  # reservation-events 스트림 소비
│   │   │   │   │   └── CancelStreamListener.java       # cancel_stream 소비 → 빈자리 알림
│   │   │   │   ├── repository/
│   │   │   │   │   ├── ReservationRepository.java
│   │   │   │   │   └── WaitingRepository.java
│   │   │   │   └── dto/
│   │   │   │       ├── ReservationRequest.java
│   │   │   │       └── ReservationResponse.java
│   │   │   ├── review/
│   │   │   │   ├── controller/ReviewController.java
│   │   │   │   ├── service/ReviewService.java
│   │   │   │   ├── entity/Review.java
│   │   │   │   └── repository/ReviewRepository.java
│   │   │   ├── stylist/
│   │   │   │   ├── controller/StylistController.java
│   │   │   │   ├── service/
│   │   │   │   │   ├── StylistService.java
│   │   │   │   │   └── StylistServiceImpl.java
│   │   │   │   ├── entity/
│   │   │   │   │   ├── StylistProfile.java
│   │   │   │   │   ├── StylistServiceItem.java       # duration 필드 (durationMinutes 아님)
│   │   │   │   │   └── OperatingHours.java
│   │   │   │   ├── repository/
│   │   │   │   │   ├── StylistProfileRepository.java
│   │   │   │   │   ├── StylistServiceRepository.java
│   │   │   │   │   └── OperatingHoursRepository.java
│   │   │   │   └── dto/
│   │   │   │       ├── StylistProfileResponse.java
│   │   │   │       ├── ServiceRequest.java
│   │   │   │       ├── ServiceResponse.java
│   │   │   │       ├── WorkingHoursRequest.java
│   │   │   │       ├── WorkingHoursResponse.java
│   │   │   │       └── UpdateStylistProfileRequest.java
│   │   │   └── user/
│   │   │       ├── controller/UserController.java
│   │   │       ├── service/
│   │   │       │   ├── UserService.java
│   │   │       │   └── UserServiceImpl.java
│   │   │       ├── entity/User.java
│   │   │       ├── repository/UserRepository.java
│   │   │       └── dto/
│   │   │           ├── UserResponse.java
│   │   │           ├── UpdateUserRequest.java
│   │   │           ├── ChangePasswordRequest.java
│   │   │           └── UpgradeToStylistRequest.java
│   │   └── resources/
│   │       └── application.yaml
│   └── test/
│       └── java/beauty/beauty/
│           ├── BeautyApplicationTests.java
│           └── reservation/
│
└── frontend/
    ├── package.json
    ├── vite.config.js
    ├── index.html                # Kakao Maps SDK: autoload=false
    └── src/
        ├── main.js
        ├── App.vue               # 로그인 감지 → notificationStore connect/disconnect
        ├── style.css
        ├── assets/main.css       # 디자인 토큰 (--primary, --accent, --success 등)
        ├── api/
        │   ├── index.js          # axios 인스턴스 + 공통 인터셉터 (토큰 자동 주입, 401 refresh)
        │   ├── auth.js
        │   ├── user.js
        │   ├── stylist.js
        │   ├── reservation.js    # reservationApi + waitingApi export
        │   ├── payment.js        # prepare(data) — 객체 그대로 전달
        │   └── ranking.js
        ├── stores/
        │   ├── authStore.js      # Pinia - JWT 토큰/유저 상태 관리
        │   └── notificationStore.js  # STOMP WebSocket 알림 상태
        ├── router/
        │   └── index.js          # Vue Router (navigation guard 포함)
        ├── components/
        │   ├── Navbar.vue        # 알림 벨 아이콘 + 드롭다운
        │   ├── StylistCard.vue
        │   └── ChatWidget.vue
        └── views/
            ├── HomeView.vue      # 카카오 지도 (kakao.maps.load 콜백 패턴)
            ├── LoginView.vue
            ├── RegisterView.vue
            ├── OAuth2CallbackView.vue
            ├── MyPageView.vue
            ├── RankingView.vue
            ├── StylistDetailView.vue
            ├── StylistManageView.vue
            ├── StylistReservationsView.vue
            ├── BookingView.vue   # 마감 슬롯 클릭 → 빈자리 알림 모달
            ├── PaymentView.vue
            ├── PaymentSuccessView.vue
            ├── PaymentFailView.vue
            ├── ChatView.vue
            └── NotFoundView.vue
```

## 빌드 & 테스트

```bash
# Redis 실행 (앱 실행 전 필수)
brew services start redis

# Backend (루트에서)
./gradlew build          # 빌드 + 테스트
./gradlew test           # 테스트만
./gradlew bootRun        # 개발 서버 실행 (포트 8080)

# Frontend (frontend/ 디렉토리에서)
npm install
npm run dev              # 개발 서버 실행 (포트 5173)
npm run build            # 프로덕션 빌드

# k6 부하 테스트 (루트에서)
k6 run k6/test_stylist_detail.js
k6 run -e STYLIST_IDS=1,2,3 k6/test_stylist_cache.js
k6 run -e TOKEN="Bearer eyJ..." -e STYLIST_ID=1 -e SERVICE_ID=1 k6/test_distributed_lock.js
```

## Redis 사용 현황

| 용도 | 키 패턴 | TTL | 위치 |
|---|---|---|---|
| JWT 블랙리스트 | `blacklist:{token}` | 토큰 잔여 유효시간 | `TokenBlacklistService` |
| 예약 시간대 캐시 | `booked_times::{stylistId}:{date}` | 30분 | `ReservationServiceImpl` |
| 채팅방 목록 캐시 | `chat_rooms::{userId}` | 1분 | `ChatServiceImpl` |
| 채팅 Pub/Sub | `chat:room:{roomId}` | - | `ChatServiceImpl` → `RedisMessageSubscriber` |
| 로그인 rate limit | `rate:login:{ip}` | 1분 | `RateLimitFilter` |
| 랭킹 ZSET | `ranking:{district}` | 상시 | `RankingServiceImpl` |
| 예약 분산락 | `lock:reservation:{stylistId}:{datetime}` | 5초 | `ReservationServiceImpl` |
| 예약 이벤트 Stream | `reservation-events` | - | `ReservationStreamListener` |
| 빈자리 알림 Stream | `cancel_stream` | - | `CancelStreamListener` |

## 도메인 요약

### 결제 (payment)
- `Payment` 엔티티: `PayStatus(PENDING→PAID→REFUNDED)`, `Method(TOSS/NAVER_PAY/KAKAO_PAY)`
- `Payment`와 `Reservation`은 `@OneToOne` 관계
- 플로우: `prepare(PENDING 생성)` → 프론트 토스 위젯 → `confirm(PAID)` → 필요시 `refund(REFUNDED)`
- PENDING 상태 10분 후 `PaymentCleanupScheduler`가 자동 삭제
- `HttpClient`는 `PaymentServiceImpl`의 인스턴스 변수로 관리 (재사용)
- **프론트 API**: `paymentApi.prepare(data)` — data 객체를 그대로 전달 (이중 래핑 금지)

### 예약 (reservation)
- `Reservation`은 `User`, `StylistServiceItem` 참조
- `totalPrice` 필드가 결제 금액 기준
- 예약 확정 시 채팅방 자동 생성 (`ChatServiceImpl.createRoomForReservation`)
- Redis 분산락으로 동시 예약 방지
- 예약 생성 → `reservation-events` Stream 발행 → 랭킹 재계산 + WebSocket 알림

### 빈자리 알림 (waiting)
- 마감 슬롯 클릭 → `POST /api/waiting/stylists/{id}?date=&time=`
- `Waiting` 엔티티에 저장
- 예약 취소 → `cancel_stream` 발행 → `CancelStreamListener` → `NotificationService.notifyWaitingAvailable()`
- WebSocket `/topic/notification/{userId}`로 실시간 알림 전송

### 알림 (notification)
- `NotificationService`: WebSocket `SimpMessagingTemplate`으로 발송 (`@Async`)
- 타입: `RESERVATION_CREATED`, `WAITING_AVAILABLE`
- 프론트: `notificationStore` (Pinia) — STOMP 구독, 읽음 상태 관리
- Navbar 벨 아이콘에 미읽음 배지 표시

### 랭킹 (ranking)
- `GET /api/ranking?district=` — Redis ZSET에서 O(log N) 조회
- 예약 생성 시 `RankingService.recalculateScore(Long stylistProfileId)` 비동기 호출
- 베이지안 알고리즘: reviewCount + avgRating + recentBookings(30일)

### 인증
- 컨트롤러에서 `@LoginUserId Long userId`로 현재 로그인 유저 ID 주입
- JWT 토큰은 HTTP Authorization 헤더로 전달
- 로그아웃 시 access token을 Redis 블랙리스트에 등록 → 만료 전 토큰 무효화

## 예외 처리 컨벤션
- `CustomException(ErrorCode)` → `GlobalExceptionHandler` → `{code, message}` JSON
- `IllegalArgumentException` → 400 BAD_REQUEST
- `IllegalStateException` → 409 CONFLICT
- `ErrorCode` enum에 HTTP 상태 + 메시지 정의됨

## API 베이스 경로
- `/api/auth/**`
- `/api/users/**`
- `/api/stylists/**`
- `/api/reservations/**`
- `/api/payments/**`
- `/api/reviews/**`
- `/api/chat/**`
- `/api/ranking/**`
- `/api/waiting/**`
- `/api/favorites/**`

## 환경변수 (.env) — 커밋 금지
- `toss.secret-key` → 토스페이먼츠 시크릿 키
- `REDIS_HOST`, `REDIS_PORT` → Redis 연결 (기본값: localhost:6379)
- DB, JWT, OAuth2, SMTP 설정 포함
- `spring-dotenv` 라이브러리로 `.env` → Spring 환경변수 자동 주입
- `KAKAO_REST_API_KEY=4b99eef97fd6cc9b8f07529eb48a3732`

## 주의사항 / 알려진 패턴
- `@LoginUserId`는 커스텀 애노테이션 + `HandlerMethodArgumentResolver`로 동작
- **`@Builder` 주의**: 필드 선언부 기본값(`= LocalDateTime.now()` 등)은 `@Builder.Default` 없이는 무시됨
- `Reservation` 엔티티: `status`, `createdAt`에 `@Builder.Default` 적용됨
- 결제 위젯 플로우: `prepare()` → 토스 SDK `renderPaymentMethods` → `requestPayment()` → 리다이렉트 → `/payment/success` 또는 `/payment/fail`
- SDK 없는 개발환경: prepare 후 mock paymentKey로 confirm 직접 호출
- `getMyPayments`: `reservationRepository.findByUserId` → `paymentRepository.findByReservationIdIn` 2-query 패턴
- **Jackson 3.x**: Spring Boot 4.0.3은 `tools.jackson.*` 패키지 사용 (`com.fasterxml.jackson.databind.*` 아님)
  - `ObjectMapper` → `tools.jackson.databind.ObjectMapper`
  - `GenericJackson2JsonRedisSerializer` → `GenericJacksonJsonRedisSerializer`
  - `@Jacksonized` 사용 불가 → `@JsonDeserialize(builder=...)` + `@JsonPOJOBuilder` 직접 사용
  - **DTO `@Setter` 필수**: `@NoArgsConstructor`만 있으면 Jackson이 필드 세팅 불가 → `@Setter` 추가
- **Kakao Maps**: `autoload=false` + `window.kakao.maps.load(callback)` 패턴 사용
- **Redis Stream `this` 주의**: `@PostConstruct`에서 `listenerContainer.receive(..., this)`로 등록 시 raw bean 전달 → `@Transactional` 미적용 → Lazy proxy 초기화 불가. Listener 내부에서 ID만 추출해 서비스에 전달할 것
- **`StylistServiceItem.duration`**: 필드명은 `duration` (프론트의 `durationMinutes`와 다름). `ReservationResponse`에 `serviceDuration`으로 매핑
- **Vue `v-if` + DOM 초기화**: `isNearbyMode = true` 후 `await nextTick()` 필수, 그 후 `kakao.maps.load()` 호출

## 에이전트 팀 & 워크트리 개발 워크플로우

### 에이전트 구성 (`.claude/agents/`)
| 에이전트 | 역할 | 모델 |
|---|---|---|
| `manager-agent` | 전체 조율 오케스트레이터 | opus |
| `planner-agent` | 설계 및 태스크 분해 | opus |
| `backend-agent` | 백엔드/프론트 구현 | sonnet |
| `test-agent` | 테스트 작성 및 실행 | sonnet |
| `review-agent` | 코드 리뷰 | sonnet |

### 워크트리 기반 개발 규칙
모든 기능 구현 및 버그 수정은 새 Git 워크트리에서 진행한다.

#### 1. 워크트리 생성
```bash
# 기능 개발
git worktree add ../beauty-feature-<기능명> -b feature/<기능명>

# 버그 수정
git worktree add ../beauty-fix-<이슈명> -b fix/<이슈명>
```

#### 2. 워크트리에서 작업
```bash
cd ../beauty-feature-<기능명>
# backend-agent, test-agent, review-agent 순서로 작업
```

#### 3. 검증 (워크트리 내에서)
```bash
./gradlew test                    # 테스트 전체 통과 필수
./gradlew build -x test           # 빌드 성공 확인
cd frontend && npm run build      # 프론트 빌드 확인
```

#### 4. 병합 (검증 통과 후)
```bash
# main 브랜치로 이동해 병합
git checkout main
git merge --no-ff feature/<기능명> -m "feat: <기능명>"

# 워크트리 정리
git worktree remove ../beauty-feature-<기능명>
git branch -d feature/<기능명>
```

#### 워크트리 규칙
- `main` 브랜치에서 직접 구현 금지
- 워크트리 경로는 항상 프로젝트 루트 밖 (`../beauty-*`)
- 병합 조건: `./gradlew test` 전체 통과 + review-agent PASS
- 병합 후 워크트리 즉시 삭제
