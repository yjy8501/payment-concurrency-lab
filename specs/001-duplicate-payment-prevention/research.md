# Research: 이중 결제 방지 실험

spec의 요구사항을 구현하기 위해 내린 기술 결정을 정리한다. 각 항목은 결정 · 근거 · 검토한 대안 순서로 쓴다.

## R1. 기술 스택 버전

- **Decision**: Kotlin 2.3.21, Spring Boot 4.1.1, JDK 21, Gradle Kotlin DSL(Wrapper 포함),
  PostgreSQL 17, Redis 7, Testcontainers 2.0.5, JUnit 5.
- **Rationale**: 2026-10 기준 최신 안정 버전이다. Kotlin은 최신(2.4.x) 대신 Spring Initializr가 Spring Boot 4.1.1과 함께
  쓰도록 지정한 2.3.21을 쓴다(공식 지원 조합). Gradle Wrapper를 넣어 JDK와 Docker만
  있으면 실행되게 한다(원칙: 기술 제약).
- **Alternatives considered**: Spring Boot 3.5 — 오픈소스 지원 기간이 끝나 새 데모에 쓸 이유가 없다.

## R2. 영속성 계층

- **Decision**: Spring Data JPA(Hibernate)를 쓰고, 방식 ④의 조건부 상태 전이는 `@Modifying` 벌크 UPDATE로 실행한다.
  스키마는 Flyway 마이그레이션으로 관리한다.
- **Rationale**: 방식 ②(버전 충돌 감지)는 JPA `@Version`이 가장 널리 알려진 형태라, 검토자가 익숙한 코드로 비교할 수 있다.
  방식 ①도 JPA의 「조회 → 변경 감지 → 저장」 흐름이 가장 흔한 실수 패턴이다.
- **Alternatives considered**: JdbcClient만 사용 — ②를 직접 `WHERE version = ?`로 쓰면 ④와 코드가 거의 같아져
  「프레임워크가 감춰 주는 낙관적 락」과 「상태 자체를 조건으로 거는 전이」의 차이가 흐려진다.

## R3. 방식별 구현 방법

| 방식 | 구현 | 승인 기록 테이블 |
|---|---|---|
| ① 조회 후 저장 | 결제 조회 → 상태가 대기면 승인으로 변경 → 승인 기록 저장(한 트랜잭션) | `approval_unguarded` |
| ② 버전 충돌 감지 | ①과 같은 흐름 + 결제 엔티티 `@Version`. 충돌 시 커밋 단계에서 예외 | `approval_unguarded` |
| ③ 만료 잠금 | Redis `SET key token NX PX ttl`로 잠금 획득 → ① 흐름 실행 → 커밋 후 Lua로 「내 토큰일 때만 삭제」. TTL 기본 5초, 만료 실험에서는 0.1초 | `approval_unguarded` |
| ④ 조건부 전이 + 유일 제약 | `UPDATE payment SET status='APPROVED' WHERE id=? AND status IN ('PENDING','NEEDS_REVIEW')` 결과가 1행일 때만 승인 기록 저장. 0행이면 「이미 처리됨」 | `approval` (결제당 유일 제약) |
| ⑤ 행 잠금 | `SELECT ... FOR UPDATE`(JPA `@Lock(PESSIMISTIC_WRITE)`)로 결제를 잠가 읽음 → 대기면 승인 → 승인 기록 저장. 다른 요청은 커밋까지 기다린 뒤 최신 상태를 읽음 | `approval_unguarded` |

- **Rationale**:
  - ④의 핵심은 「상태 확인」과 「상태 변경」을 DB 한 문장으로 합치는 것이다. PostgreSQL은 READ COMMITTED에서
    같은 행을 바꾸려는 다른 UPDATE를 기다리게 한 뒤 WHERE 조건을 다시 확인하므로, 1행이 바뀌는 요청은 하나뿐이다.
  - 유일 제약은 코드가 잘못되어도 승인 기록이 2건 생기지 않게 하는 마지막 안전망이다.
  - ③의 잠금 해제는 커밋 이후에 한다. 커밋 전에 풀면 TTL과 상관없이 다른 요청이 커밋 전 상태(대기)를 읽는다.
- **Alternatives considered**:
  - Redisson 같은 잠금 라이브러리 — 잠금 연장(watchdog) 기능이 TTL 문제를 가려서, 이 실험이 보여주려는
    「만료 시간이 있는 잠금의 한계」가 드러나지 않는다. 직접 짠 최소 구현이 비교 목적에 맞다.
  - ⑤를 유일 제약이 있는 `approval`에 기록하기 — ⑤ 자체의 정확성을 보여주려면 제약 없는 테이블에서도 1건이어야 한다.
    그래서 ⑤는 `approval_unguarded`에 기록한다. ④와의 차이는 정확성이 아니라 「잠금을 기다리는 시간」이며, 비교표의 처리 시간으로 보여준다.

## R4. 승인 기록 테이블을 둘로 나누는 이유

- **Decision**: ④는 유일 제약이 있는 `approval`, ①②③⑤는 제약이 없는 `approval_unguarded`에 기록한다. 두 테이블의 열 구성은 같다.
- **Rationale**: ①②③⑤도 유일 제약이 있는 테이블에 쓰면, 중복 승인이 「제약 위반 오류」로 바뀌어 버려
  각 방식이 실제로 몇 번 승인하려 했는지 측정할 수 없다(원칙 III: 실패도 보여주기).
- **Alternatives considered**: 방식별로 DB 스키마를 따로 두기 — 비교는 공정하지만 설정이 복잡해져 원칙 VI(단순하게)에 어긋난다.

## R5. 결제 엔티티 매핑

- **Decision**: 같은 `payment` 테이블에 엔티티 두 개를 매핑한다. `Payment`(버전 검사 없음, ①③④⑤용)와
  `VersionedPayment`(`@Version`, ②용).
- **Rationale**: 한 엔티티에 `@Version`을 붙이면 ①·③에도 낙관적 락이 적용되어 방식끼리 섞인다.
- **Alternatives considered**: 방식별 테이블 — R4와 같은 이유로 제외.

## R6. 동시 요청을 보내는 방법

- **Decision**: 테스트가 애플리케이션을 임의 포트로 띄우고, 실제 HTTP 요청 100개를 `CountDownLatch`로 동시에 출발시킨다.
  DB 커넥션 풀은 50개로 설정한다.
- **Rationale**: 실제 운영과 같은 경로(HTTP → 트랜잭션 → DB)로 경쟁시켜야 결과를 믿을 수 있다(원칙 I).
  풀을 기본값(10)보다 키워 동시에 DB에 닿는 요청 수를 늘린다.
- **Alternatives considered**: 서비스 메서드 직접 호출 — 더 빠르지만 HTTP 계층의 스레드·응답 매핑을 건너뛴다.

## R7. 가짜 PG와 응답 대기 시간

- **Decision**: 가짜 PG는 애플리케이션 안의 `PgClient` 구현체로 둔다. 승인 요청은 별도 스레드에서 실행하고
  `CompletableFuture`에 3초 타임아웃을 건다. 지연·유실·거래조회 결과는 테스트에서 시나리오로 설정한다.
  대기 시간·조회 간격·조회 횟수는 설정값으로 둔다(기본 3초·1초·3회).
- **Rationale**: 실제 PG 서버를 따로 띄우지 않아도 「응답이 늦거나 사라지는 상황」을 원하는 대로 재현할 수 있다.
  가짜 PG는 「실제로는 승인했지만 응답만 유실됨」 상태를 기억해, 거래조회 때 그 결과를 돌려준다.
- **Alternatives considered**: WireMock 등으로 별도 HTTP 서버 흉내 — 더 현실적이지만, 「응답은 유실됐지만
  PG 내부에서는 승인됨」 같은 상태를 다루기가 번거롭다.

## R8. 거래조회 실행 방식

- **Decision**: 승인 요청이 타임아웃되면 같은 요청 흐름 안에서 바로 1번째 거래조회를 하고, 결과가 없으면
  1초 간격으로 2번 더(총 3번) 조회한다(동기 실행). 최악의 경우 승인 요청 후 약 5초 안에 확정된다.
  확정되면 ④와 같은 조건부 전이로 상태를 바꾸고, 끝까지 모르면 「확인 필요」로 바꾼다.
- **Rationale**: 테스트에서 결과를 바로 확인할 수 있고, 스케줄러 없이도 spec의 시간 기준(SC-005)을 만족한다.
  상태 변경을 ④로 통일했기 때문에, 조회 도중 승인 알림이 와도 승인 기록은 1건이다(US3 시나리오 4).
- **Alternatives considered**: 스케줄러로 주기적 재조회 — 운영에서는 더 일반적이지만, 실험 범위를 넘는다(원칙 VI).

## R9. 비교 결과 표 생성

- **Decision**: `StrategyComparisonTest`가 다섯 방식을 10번씩 실행하고 결과를 `build/reports/comparison.md`로 쓴다.
  열은 승인 기록 수, 「이미 처리됨」 수, 오류 수, 전체 처리 시간(100개가 모두 끝날 때까지), 평균 응답 시간이다.
  `./gradlew comparison` 명령 하나로 실행한다. README의 결과 표는 이 파일을 옮겨 적고, 측정 환경(CPU·OS)을 함께 적는다.
- **Rationale**: SC-003(명령 한 번으로 같은 표 재생성)과 원칙 II를 만족한다. 처리 시간은 「정확하지만 느리지 않은가」라는
  질문에 답하기 위한 참고값이며, 첫 실행의 JVM 예열 영향을 줄이려고 측정 전에 한 번 예열 실행을 한다.

## R10. 실패 시 반쪽 반영 검증 (FR-006)

- **Decision**: 테스트용 오류 주입 훅으로 「상태 변경 후, 승인 기록 저장 전」에 예외를 일으키고,
  결제 상태와 승인 기록이 둘 다 바뀌지 않았는지 확인한다. ④ 방식에 대해 검증한다.
- **Rationale**: 상태 변경과 승인 기록이 한 트랜잭션이라는 주장은 실제로 중간에 실패시켜 봐야 증명된다.
- **Alternatives considered**: 코드 리뷰로만 확인 — 원칙 II(숫자로 증명)에 어긋난다.
