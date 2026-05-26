# 개발 워크플로우

## 에이전트 구성

| 에이전트 | 역할 | 도구 |
|---|---|---|
| `manager-agent` | 전체 조율 오케스트레이터 | Read, Grep, Glob, Bash |
| `planner-agent` | 설계 및 태스크 분해 (코드 수정 금지) | Read, Grep, Glob |
| `backend-agent` | 백엔드/프론트 구현 (워크트리에서만) | Read, Edit, Write, Bash, Grep, Glob |
| `test-agent` | 테스트 작성 및 실행 (구현 코드 수정 금지) | Read, Write, Edit, Bash, Grep, Glob |
| `review-agent` | 코드 리뷰 (코드 수정 금지) | Read, Grep, Glob |

## 표준 작업 흐름

### 새 기능 구현
```
1. planner-agent  → 기능 분석 + 구현 계획 수립
2. worktree 생성  → git worktree add ../beauty-feature-<기능명> -b feature/<기능명>
3. backend-agent  → 워크트리에서 구현 + 빌드 확인
4. test-agent     → 워크트리에서 테스트 작성 + 실행
5. review-agent   → 코드 리뷰
6. 병합           → main merge + worktree 삭제
```

### 버그 수정
```
1. review-agent   → 원인 분석
2. worktree 생성  → git worktree add ../beauty-fix-<이슈> -b fix/<이슈>
3. backend-agent  → 수정
4. test-agent     → 재현 테스트 + 회귀 테스트
5. 병합
```

## 워크트리 규칙
- **main 브랜치 직접 구현 금지**
- 경로: `../beauty-<type>-<name>` (프로젝트 루트 밖)
- 병합 조건: `./gradlew test` 전체 통과 + review-agent PASS
- 병합 후 즉시 삭제: `git worktree remove ../beauty-<type>-<name>`

## 각 단계 검증 기준

| 단계 | 검증 방법 |
|---|---|
| backend-agent | `./gradlew build -x test` 성공 + `cd frontend && npm run build` 성공 |
| test-agent | `./gradlew test` 전체 통과 |
| review-agent | 블로커 0건 (경고는 권장) |

## 플래너 출력 형식
```
## 기능명

### 요구사항
...

### 백엔드 구현
1. [신규] path/to/File.java — 설명
2. [수정] path/to/Other.java — 변경 내용

### 프론트엔드 구현
1. [신규] frontend/src/api/xxx.js
2. [수정] frontend/src/views/XxxView.vue

### 검증 기준
- ./gradlew build -x test 성공
- ...

### 리스크
- Jackson 3.x 패키지 변경 여부
- @Builder.Default 누락 가능성
- Redis 캐시 무효화 필요 여부
- 분산락 필요 여부 (동시성 이슈)
```

## 리뷰 출력 형식
```
## 리뷰 결과: PASS / FAIL

### 블로커 (병합 불가)
- [파일명:라인] 문제 → 수정 방향

### 경고 (권장 수정)
- [파일명:라인] 문제 → 수정 방향

### 정보
- 특이사항
```
FAIL 시 backend-agent에 블로커 전달 → 수정 후 재리뷰.
PASS 시 manager-agent에 병합 승인.

---

## 자동화 회귀 테스트 규칙

> 새 기능 추가나 코드 수정이 다른 기능을 조용히 깨뜨리는 회귀 버그를 방지하기 위한 규칙이다.
> 변경한 파일의 테스트만 통과해도 연관 기능이 깨질 수 있으므로, 병합 전 전체 테스트를 반드시 실행한다.

### 병합 조건 (건너뛰기 금지)

```
1. ./gradlew test          — 전체 테스트 통과 (단위 + 통합)
2. cd frontend && npm run build  — 프론트엔드 빌드 성공
3. review-agent PASS       — 블로커 0건
```

위 세 조건이 모두 충족되지 않으면 main 브랜치 병합 불가.

### 회귀 테스트 작성 규칙

test-agent는 새 기능 테스트 외에 **영향받는 연관 기능의 회귀 테스트**도 함께 작성한다.

| 변경 영역 | 함께 검증해야 할 회귀 대상 |
|---|---|
| 예약 로직 수정 | 결제 플로우, 캐시 무효화, 알림 발송 |
| 인증/보안 수정 | 모든 보호 API 접근 가능 여부 |
| Redis 캐시 수정 | 캐시 히트 / 미스 시나리오 모두 |
| 엔티티 변경 | 연관 엔티티 조회, N+1 발생 여부 |

### CI 파이프라인 규칙 (GitHub Actions)

feature/* 또는 fix/* 브랜치에서 main으로 PR 생성 시 자동 실행:

```yaml
# .github/workflows/ci.yml 에 정의
trigger: PR to main (feature/*, fix/*)

steps:
  1. ./gradlew test              # 전체 백엔드 테스트
  2. npm run build               # 프론트엔드 빌드
  3. 실패 시 PR 병합 차단 (required status check)
```

**CI 실패 시 에이전트 행동 규칙:**
- 테스트 실패 로그를 먼저 확인한다
- 테스트를 수정해서 통과시키는 것이 아니라 원인이 된 구현 코드를 수정한다
- 수정 후 test-agent가 회귀 여부를 재검증한다
- `--no-verify` 또는 테스트 skip 옵션 사용 금지
