---

description: "Task list for 이중 결제 방지 실험"
---

# Tasks: 이중 결제 방지 실험

**Input**: Design documents from `/specs/001-duplicate-payment-prevention/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/http-api.md, quickstart.md

**Tests**: 포함한다. 원칙 I·II·III과 spec의 성공 기준(SC-001~006)이 실제 DB 동시성 테스트를 요구한다.

**Organization**: 사용자 스토리별로 묶어, 스토리 하나만 끝내도 따로 테스트할 수 있게 한다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일을 다루고 미완료 작업에 의존하지 않아 병렬 진행 가능
- **[Story]**: 해당 사용자 스토리 (US1, US2, US3)

## Path Conventions

- 메인 코드: `src/main/kotlin/io/github/yjy8501/paymentlab/`
- 테스트 코드: `src/test/kotlin/io/github/yjy8501/paymentlab/`

---

## Phase 1: Setup (프로젝트 뼈대)

- [X] T001 Gradle Kotlin DSL 프로젝트와 Wrapper 생성: `settings.gradle.kts`, `build.gradle.kts`, `gradlew`, `gradle/wrapper/` (Kotlin 2.3.21, Spring Boot 4.1.1, JDK 21 toolchain)
- [X] T002 의존성 추가 in `build.gradle.kts`: Web MVC, Data JPA, Data Redis, Flyway(PostgreSQL), PostgreSQL 드라이버, Jackson Kotlin, 테스트용 Spring Boot Test·Testcontainers 2.0.5(PostgreSQL)·JUnit 5
- [X] T003 [P] `.gitignore` 작성 (build/, .gradle/, .idea/, `.claude/settings.local.json`)
- [X] T004 [P] 애플리케이션 진입점 생성 in `src/main/kotlin/io/github/yjy8501/paymentlab/PaymentLabApplication.kt`
- [X] T005 [P] 설정 파일 작성 in `src/main/resources/application.yml` (Hikari 풀 50, PG 대기 3초, 거래조회 간격 1초·총 3회, ③ 잠금 TTL 5초)

---

## Phase 2: Foundational (모든 스토리가 쓰는 기반)

**이 단계가 끝나야 사용자 스토리 작업을 시작할 수 있다**

- [X] T006 Flyway 마이그레이션 작성 in `src/main/resources/db/migration/V1__init.sql`: `payment`(payment_no varchar(40) UNIQUE, amount bigint > 0, status varchar(20) `PENDING`·`APPROVED`·`FAILED`·`NEEDS_REVIEW`, version bigint), `approval`(payment_id **UNIQUE** FK, pg_tx_id varchar(40), amount, approved_at), `approval_unguarded`(같은 열, 유일 제약 없음)
- [X] T007 [P] `PaymentStatus` enum과 `Payment` 엔티티 in `src/main/kotlin/io/github/yjy8501/paymentlab/payment/Payment.kt`
- [X] T008 [P] `Approval`, `UnguardedApproval` 엔티티와 저장소 in `src/main/kotlin/io/github/yjy8501/paymentlab/approval/`
- [X] T009 `PaymentRepository` (결제 번호로 조회, 조건부 전이 `UPDATE ... WHERE id = ? AND status IN ('PENDING','NEEDS_REVIEW')`) in `src/main/kotlin/io/github/yjy8501/paymentlab/payment/PaymentRepository.kt`
- [X] T010 결제 생성 API `POST /payments` in `src/main/kotlin/io/github/yjy8501/paymentlab/payment/PaymentController.kt`
- [X] T011 [P] Testcontainers 공통 설정(PostgreSQL 17·Redis 7, `@ServiceConnection`, 컨테이너 재사용) in `src/test/kotlin/io/github/yjy8501/paymentlab/support/TestContainersConfig.kt`
- [X] T012 [P] 동시 요청 실행기 `ConcurrentCaller` (N개 요청을 `CountDownLatch`로 동시 출발, 응답 종류별 개수·전체 처리 시간·평균 응답 시간 집계) in `src/test/kotlin/io/github/yjy8501/paymentlab/support/ConcurrentCaller.kt`
- [X] T013 기반 동작 확인 테스트 (컨테이너 기동, 마이그레이션, 결제 생성) in `src/test/kotlin/io/github/yjy8501/paymentlab/payment/PaymentCreationTest.kt`

**Checkpoint**: `./gradlew test`로 컨테이너가 뜨고 결제를 만들 수 있다

---

## Phase 3: User Story 1 - 중복 승인 알림을 받아도 승인은 1번 (Priority: P1) - MVP

**Goal**: 방식 ④로 같은 승인 알림 100개를 동시에 받아도 승인 기록은 정확히 1건

**Independent Test**: 결제 1건 생성 → `conditional-update`로 알림 100개 동시 전송 → 승인 1건, 「이미 처리됨」 99개, 오류 0개

### Tests for User Story 1

- [X] T014 [P] [US1] 동시성 테스트: 100개 동시 알림 × 10회 반복, 매회 승인 1건·「이미 처리됨」 99개·오류 0개 (SC-001·002) in `src/test/kotlin/io/github/yjy8501/paymentlab/callback/ConditionalUpdateConcurrencyTest.kt`
- [X] T015 [P] [US1] 엣지 케이스 테스트: 이미 승인된 결제 재알림, 없는 결제(404 `PAYMENT_NOT_FOUND`), 금액 불일치(422 `AMOUNT_MISMATCH`), 승인·실패 알림 동시 도착 시 결과 하나만 반영, 「확인 필요」 결제의 승인 in `src/test/kotlin/io/github/yjy8501/paymentlab/callback/CallbackEdgeCaseTest.kt`
- [X] T016 [P] [US1] 반쪽 반영 없음 테스트: 「상태 변경 후, 승인 기록 저장 전」에 오류를 주입하면 결제 상태와 승인 기록이 둘 다 바뀌지 않음 (FR-006, research R10) in `src/test/kotlin/io/github/yjy8501/paymentlab/callback/AtomicityTest.kt`

### Implementation for User Story 1

- [X] T017 [P] [US1] 콜백 요청·결과 타입 (`CallbackRequest`, `CallbackResult`: `APPROVED`·`FAILED`·`ALREADY_PROCESSED`·`PAYMENT_NOT_FOUND`·`AMOUNT_MISMATCH`·`CONFLICT`)과 `CallbackStrategy` 인터페이스 in `src/main/kotlin/io/github/yjy8501/paymentlab/callback/CallbackStrategy.kt`
- [X] T018 [P] [US1] 테스트용 오류 주입 훅 `FailureInjector` (기본 꺼짐, 테스트에서 지정한 지점에서 예외 발생) in `src/main/kotlin/io/github/yjy8501/paymentlab/support/FailureInjector.kt`
- [X] T019 [US1] 방식 ④ `ConditionalUpdateStrategy` (조건부 전이 1행일 때만 `approval` 저장, 0행이면 `ALREADY_PROCESSED`, 한 트랜잭션, 오류 주입 지점 포함) in `src/main/kotlin/io/github/yjy8501/paymentlab/callback/strategy/ConditionalUpdateStrategy.kt`. 확정 로직은 US3와 함께 쓰도록 `src/main/kotlin/io/github/yjy8501/paymentlab/payment/PaymentSettler.kt`로 분리
- [X] T020 [US1] 콜백 API `POST /callbacks/{strategy}` (결과 → HTTP 상태 매핑은 contracts/http-api.md 표 그대로) in `src/main/kotlin/io/github/yjy8501/paymentlab/callback/CallbackController.kt`

**Checkpoint**: US1 테스트 통과 = MVP 완성

---

## Phase 4: User Story 2 - 다섯 가지 처리 방식 비교 (Priority: P2)

**Goal**: ①~⑤를 같은 조건으로 실행하고 정확성과 처리 시간을 표로 비교

**Independent Test**: `./gradlew comparison` → `build/reports/comparison.md`에 방식별 결과 표 생성

### Tests for User Story 2

- [ ] T021 [P] [US2] 방식 ① 중복 재현 테스트: 10회 중 1회 이상 승인 기록 2건 이상 (US2-2, SC-004) in `src/test/kotlin/io/github/yjy8501/paymentlab/callback/ReadThenSaveConcurrencyTest.kt`
- [ ] T022 [P] [US2] 방식 ② 테스트: 매회 승인 1건, 오류(409 `CONFLICT`) 1개 이상 (US2-3) in `src/test/kotlin/io/github/yjy8501/paymentlab/callback/OptimisticLockConcurrencyTest.kt`
- [ ] T023 [P] [US2] 방식 ③ TTL 만료 테스트: TTL 0.1초 + 처리 지연이 TTL보다 길 때 승인 기록 2건 이상 (US2-4, SC-004) in `src/test/kotlin/io/github/yjy8501/paymentlab/callback/RedisLockExpiryTest.kt`
- [ ] T024 [P] [US2] 방식 ⑤ 테스트: 10회 모두 승인 1건·「이미 처리됨」 99개·오류 0개 (US2-5, SC-006) in `src/test/kotlin/io/github/yjy8501/paymentlab/callback/PessimisticLockConcurrencyTest.kt`

### Implementation for User Story 2

- [ ] T025 [P] [US2] 테스트용 처리 지연 훅 `ProcessingDelay` (기본 0ms, 테스트에서 설정) in `src/main/kotlin/io/github/yjy8501/paymentlab/support/ProcessingDelay.kt`
- [ ] T026 [P] [US2] 방식 ① `ReadThenSaveStrategy` (조회 → 대기면 승인 → `approval_unguarded` 저장) in `src/main/kotlin/io/github/yjy8501/paymentlab/callback/strategy/ReadThenSaveStrategy.kt`
- [ ] T027 [P] [US2] `VersionedPayment` 엔티티(`@Version`, 같은 `payment` 테이블)와 저장소 in `src/main/kotlin/io/github/yjy8501/paymentlab/payment/VersionedPayment.kt`
- [ ] T028 [US2] 방식 ② `OptimisticLockStrategy` (충돌 예외 → `CONFLICT`, 재시도 없음) in `src/main/kotlin/io/github/yjy8501/paymentlab/callback/strategy/OptimisticLockStrategy.kt`
- [ ] T029 [P] [US2] Redis 잠금 `ExpiringLock` (`SET NX PX`로 획득, Lua로 「내 토큰일 때만 삭제」) in `src/main/kotlin/io/github/yjy8501/paymentlab/callback/strategy/ExpiringLock.kt`
- [ ] T030 [US2] 방식 ③ `RedisLockStrategy` (잠금 실패 → 대기 없이 `CONFLICT`, 커밋 후 잠금 해제) in `src/main/kotlin/io/github/yjy8501/paymentlab/callback/strategy/RedisLockStrategy.kt`
- [ ] T031 [US2] `PaymentRepository`에 잠금 조회 추가 (`@Lock(PESSIMISTIC_WRITE)`, `SELECT ... FOR UPDATE`) in `src/main/kotlin/io/github/yjy8501/paymentlab/payment/PaymentRepository.kt`
- [ ] T032 [US2] 방식 ⑤ `PessimisticLockStrategy` (잠가서 조회 → 대기면 승인 → `approval_unguarded` 저장, 이미 처리됐으면 `ALREADY_PROCESSED`) in `src/main/kotlin/io/github/yjy8501/paymentlab/callback/strategy/PessimisticLockStrategy.kt`
- [ ] T033 [US2] 비교표 생성 테스트 `StrategyComparisonTest` (예열 1회 후 5방식 × 10회, 열: 승인 기록 수·이미 처리됨·오류·전체 처리 시간·평균 응답 시간, 측정 환경 기록 → `build/reports/comparison.md`) in `src/test/kotlin/io/github/yjy8501/paymentlab/comparison/StrategyComparisonTest.kt`
- [ ] T034 [US2] Gradle `comparison` 태스크 등록 (기본 `test`에서는 비교표 테스트 제외) in `build.gradle.kts`

**Checkpoint**: `./gradlew comparison`으로 비교표가 생성된다

---

## Phase 5: User Story 3 - 응답을 못 받은 결제는 거래조회로 확정 (Priority: P3)

**Goal**: PG 응답이 3초 안에 없으면 즉시 1번째 거래조회, 이후 1초 간격 2번 더(총 3번) 조회로 확정, 실패하면 「확인 필요」

**Independent Test**: 가짜 PG를 「응답 유실 + 실제 승인」으로 설정 → 승인 요청 → 거래조회로 승인 확정

### Tests for User Story 3

- [ ] T035 [P] [US3] PG 정상 응답 테스트: 바로 승인 → `APPROVED`(`PG_RESPONSE`), 바로 실패 → `FAILED`(`PG_RESPONSE`) (US3-1·2) in `src/test/kotlin/io/github/yjy8501/paymentlab/pg/PgApprovalTest.kt`
- [ ] T036 [P] [US3] 타임아웃·거래조회 테스트: 조회 결과 승인 → `APPROVED`(`INQUIRY`), 실패 → `FAILED`, 3회 모두 결과 없음 → `NEEDS_REVIEW`(`UNRESOLVED`), 승인 요청 후 약 5초 안에 확정 (US3-3~5, SC-005) in `src/test/kotlin/io/github/yjy8501/paymentlab/pg/PgTimeoutReconciliationTest.kt`
- [ ] T037 [P] [US3] 거래조회 중 승인 알림 동시 도착 → 승인 기록 1건 (US3-6) in `src/test/kotlin/io/github/yjy8501/paymentlab/pg/ReconcileWithCallbackRaceTest.kt`

### Implementation for User Story 3

- [ ] T038 [P] [US3] `PgClient` 인터페이스와 결과 타입(`PgApproveResult`, `InquiryResult`: `APPROVED(pgTxId)`·`FAILED`·`NOT_FOUND`) in `src/main/kotlin/io/github/yjy8501/paymentlab/pg/PgClient.kt`
- [ ] T039 [P] [US3] `FakePg` (시나리오 설정: 바로 승인·바로 실패·지연·유실, 「응답은 유실됐지만 실제 승인됨」 기억, 거래조회 결과) in `src/main/kotlin/io/github/yjy8501/paymentlab/pg/FakePg.kt`
- [ ] T040 [US3] 상태 확정 로직을 ④의 조건부 전이로 통일: T019에서 만든 `PaymentSettler`(승인·실패·확인 필요 반영)를 PG 승인 흐름에서 그대로 사용 in `src/main/kotlin/io/github/yjy8501/paymentlab/payment/PaymentSettler.kt`
- [ ] T041 [US3] `PaymentApprovalService` (3초 타임아웃 → `Reconciler`로 즉시 1회 + 1초 간격 2회 조회) in `src/main/kotlin/io/github/yjy8501/paymentlab/pg/PaymentApprovalService.kt`
- [ ] T042 [US3] 승인 요청 API `POST /payments/{paymentNo}/approve` in `src/main/kotlin/io/github/yjy8501/paymentlab/pg/PaymentApprovalController.kt`

**Checkpoint**: 세 스토리의 테스트가 모두 통과한다

---

## Phase 6: Polish (문서와 마무리)

- [ ] T043 [P] ADR 작성: 왜 분산 락이 아니라 조건부 전이 + 유일 제약인가 (대안: 조회 후 저장·낙관적 락·Redis 잠금·행 잠금, 채택 근거, 기각 사유, 비교표의 정확성·처리 시간 인용) in `docs/adr/0001-conditional-update-over-distributed-lock.md`
- [ ] T044 [P] ADR 작성: 승인 기록 테이블을 둘로 나눈 이유 in `docs/adr/0002-separate-unguarded-approval-table.md`
- [ ] T045 README 작성: 한 줄 소개, 결과 표(`comparison.md`에서 옮김, 측정 환경 포함), 실행 방법, SDD 진행 과정(spec → plan → tasks → 구현 → 대조) 링크 in `README.md`
- [ ] T046 quickstart.md 절차대로 전체 실행해 결과 확인 (`./gradlew test`, `./gradlew comparison`)
- [ ] T047 회사 식별자·내부 정보가 없는지 전체 검색으로 확인 (원칙 V)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → 사용자 스토리(Phase 3~5) → **Polish (Phase 6)**

### User Story Dependencies

- **US1 (P1)**: Foundational 이후 바로 시작. 다른 스토리에 의존하지 않는다.
- **US2 (P2)**: Foundational 이후 시작 가능. 비교표(T033)는 방식 ④(T019)와 콜백 API(T020)가 있어야 한다.
- **US3 (P3)**: Foundational 이후 시작 가능. 콜백 동시 도착 테스트(T037)는 T019·T020이 필요하다.

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- 타입·엔티티 → 처리 로직 → API 순서로 만든다.

## Parallel Example: User Story 2

```text
동시에 진행 가능:
T021 ReadThenSaveConcurrencyTest
T022 OptimisticLockConcurrencyTest
T023 RedisLockExpiryTest
T024 PessimisticLockConcurrencyTest
T025 ProcessingDelay
T026 ReadThenSaveStrategy
T027 VersionedPayment
T029 ExpiringLock
```

## Implementation Strategy

### MVP First

1. Phase 1·2 완료 → 2. Phase 3(US1) 완료 → 3. **멈추고 확인**: 「100개 동시 → 승인 1건」 테스트 통과

### Incremental Delivery

1. Setup + Foundational → 기반 완성
2. US1 → 정답 방식 동작 (MVP)
3. US2 → 다섯 방식 비교표 (레포의 핵심 결과물)
4. US3 → 응답 유실 처리
5. Polish → ADR, README

각 단계를 별도 커밋으로 남긴다(원칙: 개발 흐름).
