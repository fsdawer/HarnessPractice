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
