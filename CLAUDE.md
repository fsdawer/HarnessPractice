# CutIng 프로젝트 (beauty)

> **핵심 문제**: 너무 많은 미용사·미용실·광고 속에서 나에게 잘 맞는 미용사를 찾기 어렵다
> **해결**: 광고 없이 위치·취향·예산 기반으로 미용사를 매칭하는 개인화 예약 앱
> 상세 개요: `.claude/docs/project-overview.md`

## ⛔ 절대 규칙: planner-agent 우선 호출
모든 구현·수정·기능 요청 시 **planner-agent를 반드시 먼저 호출**한다.
planner가 요청을 분석하고 필요한 에이전트를 선택·지시한다.
**예외**: 단순 질문·코드 설명·오타·1줄 수정만 직접 처리 가능.
이 규칙을 건너뛰는 것은 허용되지 않는다.

## 행동 원칙
1. **생각 먼저** — 불확실하면 가정 말고 물어본다. 여러 해석이 있으면 트레이드오프 제시.
2. **최소한의 코드** — 요청된 것만. 200줄이 50줄 가능하면 다시 써라.
3. **외과적 수정** — 필요한 것만 건드린다. 내 변경이 만든 고아 코드만 정리.
4. **계획 먼저, 승인 후 실행** — 구현 전 텍스트 계획 작성 → 트레이드오프 제시 → 승인 후 실행. 예외: 오타·1줄 수정.
5. **백엔드·프론트 동시 완성** — API 만들면 `frontend/src/api/` + 화면까지 반드시 연결.
6. **진행 상황 실시간 중계** — 파일 하나 완성할 때마다 한 줄 알림. 사용자가 물어볼 필요 없어야 함.

## 기술 스택
- Backend: Spring Boot 4.0.3 / Java 17 / JPA + MySQL
- Frontend: Vue.js 3 (Vite) + Pinia + Vue Router
- Auth: JWT (jwt 0.12.6) + Spring Security + OAuth2 (Kakao/Naver)
- Redis: 캐시·Pub/Sub·분산락·Stream·랭킹ZSET·rate-limit
- 외부: 토스페이먼츠 v2, 네이버 SMTP, WebSocket(채팅), spring-dotenv

## 빌드
```bash
brew services start redis && ./gradlew bootRun   # 백엔드 :8080
cd frontend && npm run dev                        # 프론트 :5173
./gradlew test                                    # 전체 테스트
```

## 도메인 핵심
- **결제**: PENDING→PAID→REFUNDED. PENDING 10분 후 자동 삭제
- **예약**: totalPrice 기준. 확정 시 채팅방 자동 생성. Redis 분산락으로 동시성 방지
- **랭킹**: Redis ZSET O(log N). 베이지안(reviewCount + avgRating + recentBookings 30일)
- **알림**: SSE 즉시 전송. 미연결 시 Redis List 보관(7일) → 재연결 시 flushPending
- **인증**: `@LoginUserId Long userId`. 로그아웃→Redis 블랙리스트 등록

## 에이전트 로깅 규칙 (필수)

모든 에이전트는 작업 시작·완료 시 반드시 Bash로 로그를 기록한다.
로그 경로: `.claude/logs/<에이전트명>.log`

```bash
mkdir -p .claude/logs
echo "[$(date '+%H:%M:%S')] ▶ 작업내용" >> .claude/logs/<에이전트명>.log
echo "[$(date '+%H:%M:%S')] ✅ 완료내용" >> .claude/logs/<에이전트명>.log
```

## ⚠️ 필수 주의사항
- **Jackson 3.x**: `tools.jackson.*` (`com.fasterxml` 금지). Redis DTO `@Setter` 필수
- **@Builder.Default**: 필드 기본값 있으면 반드시 붙임
- **Kakao Maps**: `autoload=false` + `kakao.maps.load(callback)`. `v-if` 후 `await nextTick()` 필수
- **Redis Stream**: `@PostConstruct`에서 `this` 직접 전달 금지. ID만 추출해 서비스에 전달
- **환경변수**: `.env` 절대 커밋 금지. `spring-dotenv`로 자동 주입

## 에이전트 팀 (`.claude/agents/`, 모두 sonnet)

### 구현 팀
| 에이전트 | 역할 | 제약 |
|---------|------|------|
| planner | 설계·계획·트레이드오프 | 코드 수정 금지 |
| backend | 백엔드 구현 (엔티티/서비스/컨트롤러) | 워크트리 필수 |
| frontend | Vue.js 구현 (api/뷰/컴포넌트) | 워크트리 필수 |
| test | 테스트 작성·실행 | src/main/ 수정 금지 |
| review | 보안·성능·컨벤션 리뷰 | 수정 금지 |

### 전문가 팀
| 에이전트 | 역할 | 참고 |
|---------|------|------|
| ux-agent | 사용자 여정·화면 흐름·UX 리뷰 | `.claude/docs/project-overview.md` |
| notion-agent | 회의록·기획서·스프린트 노션 정리 | `.claude/docs/notion-guide.md` |
| architecture-agent | 기술 의사결정·설계 검토 | `.claude/docs/architecture-guide.md` |
| qa-agent | 테스트 시나리오·버그 분석 | `.claude/docs/qa-guide.md` |

### 기능 구현 순서 (건너뛰기 금지)
```
1. planner → 요청 분석 · 에이전트 선택
2. ux-agent → 사용자 여정 · 화면 흐름 설계
3. architecture-agent → 기술 설계 · 트레이드오프 분석
4. planner → 결과 취합 · 사용자 승인
5. git worktree add ../beauty-feature-<기능명> -b feature/<기능명>
6. backend + frontend (병렬 가능) → build PASS
7. test → gradlew test PASS
8. review → PASS → git merge --no-ff → worktree 삭제
9. notion-agent → 회의록·결과 노션 저장
```
