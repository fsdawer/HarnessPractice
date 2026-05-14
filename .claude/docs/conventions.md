# CutIng 코딩 컨벤션

## General Rules
- 구현 전 반드시 작업 계획을 먼저 제시한다
- 작업 범위 변경 시 사용자 승인 필수
- 대규모 리팩토링 전 반드시 설명한다
- 파일 생성 전 목적 설명 필수
- 기존 코드 삭제 전 이유 설명 필수
- 구현 후 반드시 리뷰 단계 수행
- 구현 후 반드시 테스트 수행
- 하드코딩 금지

## Database Rules
- 사용자 승인 없이 DB 스키마 변경 금지
- DROP/TRUNCATE 금지
- 운영 데이터 삭제 금지
- 마이그레이션 없이 스키마 수정 금지
- 대량 UPDATE/DELETE 전 사용자 확인 필수

## Git Rules
- 테스트 실패 시 커밋 금지
- 사용자 승인 없이 push 금지
- force push 금지
- 커밋 전 변경사항 요약 필수
- 하나의 커밋은 하나의 목적만 가진다

## Output Rules (답변 순서)
1. 문제 분석
2. 구현 계획
3. 영향 범위
4. 코드 작성
5. 테스트 전략
6. 위험 요소

## Jackson 3.x (Spring Boot 4.0.3)
- `tools.jackson.databind.ObjectMapper` — `com.fasterxml.jackson` 임포트 금지
- `GenericJacksonJsonRedisSerializer` (`2` 없음)
- `@Jacksonized` 사용 불가 → `@JsonDeserialize(builder=...)` + `@JsonPOJOBuilder` 직접 사용
- Redis 역직렬화 DTO: `@NoArgsConstructor`만 있으면 Jackson이 필드 세팅 불가 → `@Setter` 필수

## 엔티티 / JPA
- `@Builder` 사용 시 기본값 있는 필드는 반드시 `@Builder.Default`
  ```java
  @Builder.Default private ReservationStatus status = ReservationStatus.PENDING;
  ```
- `@OneToOne`, `@ManyToOne`은 지연 로딩 (`fetch = FetchType.LAZY`)
- N+1 주의: 루프 내 `findById` 호출 금지

## 예외 처리
- `CustomException(ErrorCode)` 사용
- `IllegalArgumentException` → 400, `IllegalStateException` → 409
- 컨트롤러에서 try-catch 금지 (GlobalExceptionHandler에 위임)

## Redis
- 캐시 키: `도메인::{파라미터}` (:: 구분자)
- 분산락 키: `lock:도메인:{stylistId}:{datetime}`
- Stream 리스너 내부에서 `this` 직접 사용 금지 → `@PostConstruct`에서 raw bean 전달 시 `@Transactional` 미적용됨
  → ID만 추출해 서비스 빈에 전달하는 패턴 사용

## 인증
- 컨트롤러 파라미터: `@LoginUserId Long userId`
- JWT는 Authorization 헤더로 전달, HTTP 직접 파싱 금지

## 결제
- `paymentApi.prepare(data)` — data 객체 그대로 전달 (이중 래핑 금지)
- 상태 전이 순서 강제: PENDING → PAID → REFUNDED

## 프론트엔드

### API 호출
- `frontend/src/api/index.js` axios 인스턴스 사용 (직접 `new axios()` 생성 금지)
- 401 refresh는 인터셉터에서 처리, 컴포넌트 중복 처리 금지

### Vue 컴포넌트
- Composition API (`<script setup>`) 사용
- Kakao 지도: `autoload=false` + `window.kakao.maps.load(callback)` 패턴
- `v-if`로 DOM 조건부 렌더링 후 카카오 맵 초기화 시 반드시 `await nextTick()` 먼저 호출
- `v-html` 사용 금지 (XSS)

### Pinia 스토어
- `authStore.js`: 토큰/유저 상태 (localStorage 직접 접근 금지)
- `notificationStore.js`: STOMP WebSocket 알림

## 리뷰 체크리스트 (블로커)
- `com.fasterxml.jackson` 임포트
- Redis DTO `@Setter` 누락
- `@Builder.Default` 누락
- `FetchType.EAGER` 사용
- Stream 리스너 `this` 직접 사용
- `prepare()` 데이터 이중 래핑
- `@LoginUserId` 없이 userId를 요청 파라미터로 수신
- JWT 로그 출력
- 환경변수 하드코딩 (API 키, 비밀번호)
