# CutIng 프로젝트 아키텍처

## 기술 스택
- **Backend**: Spring Boot 4.0.3 / Java 17 / JPA + MySQL
- **Frontend**: Vue.js 3 (Vite) + Pinia + Vue Router
- **Auth**: JWT (`io.jsonwebtoken:jjwt 0.12.6`) + Spring Security + OAuth2 (Kakao/Naver)
- **Cache/Pub-Sub**: Redis (Spring Data Redis 4.0.3 + Spring Cache)
- **외부 API**: 토스페이먼츠 v2, 네이버 SMTP
- **기타**: WebSocket(채팅), spring-dotenv, Lombok

## API 베이스 경로
```
/api/auth/**       /api/users/**        /api/stylists/**
/api/reservations/**  /api/payments/**  /api/reviews/**
/api/chat/**       /api/ranking/**      /api/waiting/**
/api/favorites/**
```

## Redis 사용 현황

| 용도 | 키 패턴 | TTL |
|---|---|---|
| JWT 블랙리스트 | `blacklist:{token}` | 토큰 잔여 유효시간 |
| 예약 시간대 캐시 | `booked_times::{stylistId}:{date}` | 30분 |
| 채팅방 목록 캐시 | `chat_rooms::{userId}` | 1분 |
| 채팅 Pub/Sub | `chat:room:{roomId}` | - |
| 로그인 rate limit | `rate:login:{ip}` | 1분 |
| 랭킹 ZSET | `ranking:{district}` | 상시 |
| 예약 분산락 | `lock:reservation:{stylistId}:{datetime}` | 5초 |
| 예약 이벤트 Stream | `reservation-events` | - |
| 빈자리 알림 Stream | `cancel_stream` | - |

## 도메인 요약

### 결제 (payment)
- `PayStatus`: PENDING → PAID → REFUNDED (순서 강제)
- `Payment` ↔ `Reservation` `@OneToOne`
- 플로우: `prepare(PENDING)` → 토스 위젯 → `confirm(PAID)` → `refund(REFUNDED)`
- PENDING 10분 후 `PaymentCleanupScheduler` 자동 삭제

### 예약 (reservation)
- `Reservation`은 `User`, `StylistServiceItem` 참조
- `totalPrice`가 결제 금액 기준
- 예약 확정 시 채팅방 자동 생성
- Redis 분산락으로 동시 예약 방지
- 생성 → `reservation-events` Stream → 랭킹 재계산 + WebSocket 알림

### 빈자리 알림 (waiting)
- `POST /api/waiting/stylists/{id}?date=&time=`
- 예약 취소 → `cancel_stream` → `CancelStreamListener` → `NotificationService.notifyWaitingAvailable()`
- WebSocket `/topic/notification/{userId}`로 실시간 알림

### 랭킹 (ranking)
- Redis ZSET 베이지안 알고리즘: reviewCount + avgRating + recentBookings(30일)
- 예약 생성 시 `RankingService.recalculateScore()` 비동기 호출

### 인증
- 컨트롤러: `@LoginUserId Long userId` (커스텀 애노테이션 + HandlerMethodArgumentResolver)
- 로그아웃 시 access token → Redis 블랙리스트 등록

## 주요 엔티티 필드 주의
- `StylistServiceItem.duration` (프론트의 `durationMinutes`와 다름 → `ReservationResponse`에서 `serviceDuration`으로 매핑)
- `Reservation`: `status`, `createdAt`에 `@Builder.Default` 적용됨
