---
name: bug-fix
description: 버그 수정 시 단계별 워크플로우를 안내합니다. "~안 돼", "에러 났어", "버그 있어", "왜 안 되지", "오류 확인해줘" 요청에 사용합니다.
---

# Bug Fix Workflow — CutIng

추측으로 코드를 수정하지 않습니다. **원인을 확인한 후에만** 수정합니다.

---

## 1단계: 로그 분석

먼저 에러 메시지 전문을 읽습니다.

```
수집할 정보:
- Exception 클래스명과 메시지
- Stack trace에서 beauty.beauty.* 패키지 라인 (첫 번째 내 코드 라인)
- Caused by 체인 전체
- HTTP 상태코드 (400/409/500 구분)
```

**이 프로젝트의 흔한 에러 패턴:**

| 에러 | 원인 |
|---|---|
| `InvalidDefinitionException` | Jackson 3.x 역직렬화 실패 — `@Setter` 누락 또는 `@NoArgsConstructor` 누락 (Redis DTO) |
| `LazyInitializationException` | @Transactional 밖에서 지연 로딩 접근 |
| `PlaceholderResolutionException` | 환경변수 미설정 (테스트 환경) |
| `MultipleBagFetchException` | 컬렉션 2개 이상 FETCH JOIN |
| `BeanCreationException` | Spring 컨텍스트 로드 실패 — 의존성 누락 또는 순환참조 |
| 409 Conflict | 분산락 획득 실패 또는 중복 데이터 |

---

## 2단계: 재현 단계 확인

버그를 재현할 수 있는 최소 시나리오를 정의합니다.

```
1. 어떤 API를 호출했을 때?
2. 어떤 데이터 상태에서?
3. 매번 발생하는가, 간헐적인가?
4. 특정 조건에서만 발생하는가? (로그인 상태, 특정 역할, 동시 요청 등)
```

간헐적 버그: 동시성 문제 우선 의심 (분산락, @Transactional 범위)

---

## 3단계: Root Cause 분석

Stack trace의 **내 코드 라인**부터 역추적합니다.

```
분석 순서:
1. 에러 발생 지점의 코드 읽기
2. 해당 메서드의 입력값 추적
3. 의존하는 서비스/레포지토리 확인
4. @Transactional 경계 확인
5. Redis 상태 확인 (캐시 오염, TTL 만료, 락 미해제 가능성)
```

**원인 가설을 세우고 코드에서 검증합니다.** 가설 없이 수정하지 않습니다.

---

## 4단계: 최소 수정 원칙

원인이 확인되면 **그 원인만** 수정합니다.

```
금지:
- 관련 없는 코드 리팩토링
- 예방적 null 체크 추가 (불가능한 케이스)
- 인접 코드 "개선"
- 에러를 숨기는 try-catch 추가

허용:
- 버그를 유발한 코드만 변경
- 내 변경이 만든 고아 코드 정리
```

**변경 범위 체크**: 수정한 모든 라인이 버그와 직접 연결되는지 확인.

---

## 5단계: 회귀 테스트 수행

수정 후 반드시 확인:

```bash
# 전체 테스트 실행
./gradlew test

# 특정 테스트만
./gradlew test --tests "beauty.beauty.패키지.테스트클래스"
```

**회귀 체크:**
- 수정 전에 통과하던 테스트가 여전히 통과하는가?
- 버그를 재현하는 테스트를 추가했는가? (같은 버그 재발 방지)
- 동일한 패턴이 다른 곳에도 있는가? (예: 같은 Jackson 실수)

---

## 이 프로젝트 버그 핫스팟

코드 변경 없이 먼저 확인할 것들:

```
Redis 연결: brew services start redis 실행되어 있는가?
환경변수: .env 파일 존재하는가?
Jackson import: com.fasterxml.* 가 아닌 tools.jackson.* 사용하는가?
@Builder.Default: 기본값 필드에 붙어 있는가?
@Transactional: 트랜잭션 경계 안에서 지연 로딩하는가?
Redis DTO: @Setter, @NoArgsConstructor 있는가?
```
