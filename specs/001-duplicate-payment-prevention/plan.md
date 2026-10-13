# Implementation Plan: 이중 결제 방지 실험

**Branch**: `001-duplicate-payment-prevention` | **Date**: 2026-10-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-duplicate-payment-prevention/spec.md`

## Summary

같은 PG 승인 알림 100개가 동시에 도착할 때 승인을 정확히 1번만 반영한다. 다섯 가지 처리 방식
(① 조회 후 저장, ② 버전 충돌 감지, ③ 만료 잠금, ④ 조건부 상태 전이 + 유일 제약, ⑤ 행 잠금)을 같은 API 뒤에 두고,
실제 PostgreSQL·Redis 컨테이너에서 경쟁시켜 정확성과 처리 시간을 표로 비교한다. PG 응답을 받지 못한 결제는
가짜 PG의 거래조회로 승인·실패를 확정하고, 확정하지 못하면 「확인 필요」로 남긴다.

## Technical Context

**Language/Version**: Kotlin 2.3.21, JDK 21

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Data Redis), Flyway

**Storage**: PostgreSQL 17 (결제·승인 기록), Redis 7 (방식 ③의 잠금)

**Testing**: JUnit 5, Testcontainers 2.0.5 (PostgreSQL·Redis), Spring Boot Test (임의 포트 + 실제 HTTP)

**Target Platform**: 로컬 JDK + Docker 환경 (배포 없음)

**Project Type**: web-service (단일 Spring Boot 애플리케이션)

**Performance Goals**: 동시 요청 100개 × 10회 반복 × 5개 방식 비교가 수 분 안에 끝날 것

**Constraints**: PG 응답 대기 3초, 대기 후 즉시 첫 거래조회 + 1초 간격 2회 더(총 3회), ③ 잠금 TTL 기본 5초 (모두 설정값으로 변경 가능)

**Scale/Scope**: 결제·승인 기록 테이블 3개, API 3개, 처리 방식 5개

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 확인 | 근거 |
|---|---|---|
| I. 실제 DB로 경쟁 | 통과 | Testcontainers PostgreSQL·Redis, 실제 HTTP 동시 요청 (research R6) |
| II. 결과는 숫자로 | 통과 | `./gradlew comparison`이 정확성·처리 시간 비교표 생성 (R9), 반쪽 반영 없음도 테스트로 증명 (R10) |
| III. 실패도 보여주기 | 통과 | ①·③ 중복 재현 테스트 유지, 제약 없는 승인 테이블로 중복을 셀 수 있음 (R4) |
| IV. 결정 기록 | 통과 | `docs/adr/`에 ADR 작성 (구현 단계 작업으로 포함) |
| V. 새로 작성 | 통과 | 가짜 PG 사용, 일반적인 이름 사용 (`io.github.yjy8501.paymentlab`) |
| VI. 단순하게 | 예외 2건 | 승인 테이블 2개, 결제 엔티티 매핑 2개 → Complexity Tracking 참고 |

설계 후 재확인: 위 결과 그대로 유지. `/speckit-clarify`는 spec에 확인 필요 항목이 없어 건너뛰었다.

## Project Structure

### Documentation (this feature)

```text
specs/001-duplicate-payment-prevention/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/http-api.md
└── tasks.md             # /speckit-tasks 에서 생성
```

### Source Code (repository root)

```text
src/main/kotlin/io/github/yjy8501/paymentlab/
├── PaymentLabApplication.kt
├── payment/          # Payment, VersionedPayment, PaymentStatus, 저장소, 결제 생성 API
├── approval/         # Approval, UnguardedApproval 엔티티와 저장소
├── callback/         # 콜백 API, CallbackStrategy 인터페이스, 결과 타입
│   └── strategy/     # ReadThenSave, OptimisticLock, RedisLock, ConditionalUpdate, PessimisticLock
├── pg/               # PgClient 인터페이스, FakePg, 승인 요청 + 거래조회(PaymentApprovalService)
└── support/          # 처리 지연·오류 주입(테스트용 훅), 공통 설정
src/main/resources/
├── application.yml
└── db/migration/V1__init.sql

src/test/kotlin/io/github/yjy8501/paymentlab/
├── support/          # Testcontainers 설정, 동시 요청 실행기(ConcurrentCaller)
├── callback/         # 방식별 동시성 테스트, 엣지 케이스 테스트
├── pg/               # 타임아웃·거래조회 테스트
└── comparison/       # StrategyComparisonTest (비교표 생성)

docs/adr/
├── 0001-conditional-update-over-distributed-lock.md
└── 0002-separate-unguarded-approval-table.md
README.md
```

**Structure Decision**: 단일 Spring Boot 프로젝트. 처리 방식은 `CallbackStrategy` 인터페이스 구현체 5개로 두고,
콜백 API가 경로의 `{strategy}`로 구현체를 고른다.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| 승인 기록 테이블 2개 (`approval`, `approval_unguarded`) | ①②③⑤가 실제로 몇 번 승인했는지 세려면 유일 제약이 없어야 한다 | 테이블 1개면 중복이 제약 위반 오류로 바뀌어 실패가 가려진다 (원칙 III) |
| 같은 테이블에 엔티티 2개 (`Payment`, `VersionedPayment`) | `@Version`을 방식 ②에만 적용해야 한다 | 엔티티 1개에 `@Version`을 붙이면 ①·③·⑤에도 낙관적 락이 걸려 비교가 무의미해진다 |

## Risks

- **①의 중복 재현은 확률적이다.** 10번 중 1번 이상 관찰을 기준으로 삼고(US2-2), 실패 시 반복 횟수를 늘린다.
- **③의 TTL 만료 재현**은 테스트용 처리 지연 훅(`support/`)으로 처리 시간을 TTL보다 길게 만들어 결정적으로 재현한다.
- **처리 시간은 실행 환경에 따라 흔들린다.** 측정 전에 예열 실행을 하고, README에 측정 환경을 함께 적어 참고값임을 밝힌다.
