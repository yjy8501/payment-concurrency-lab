# payment-concurrency-lab

같은 결제 승인 알림이 동시에 100번 와도 승인은 정확히 1번만 반영한다.
다섯 가지 처리 방식을 실제 PostgreSQL·Redis에서 경쟁시켜 결과를 비교한 실험 저장소다.

Kotlin · Spring Boot 4.1 · PostgreSQL 17 · Redis 7 · Testcontainers · JUnit 5

## 결과

같은 결제 1건에 같은 승인 알림 100개를 동시에 보내는 실험을 방식마다 10번 반복했다.

| 방식 | 승인 기록 수 | 중복 승인 발생 | 이미 처리됨 (평균) | 오류 (평균) | 전체 처리 시간 (평균) | 평균 응답 시간 |
|---|---|---|---|---|---|---|
| ① 조회 후 저장 | 13~32건 | 10 / 10회 | 81.8 | 0.0 | 25 ms | 7.4 ms |
| ② 버전 충돌 감지 | 1건 | 0 / 10회 | 79.4 | 19.6 | 20 ms | 6.7 ms |
| ③ 만료 잠금 (Redis) | 1건 | 0 / 10회 | 1.8 | 97.2 | 11 ms | 6.3 ms |
| **④ 조건부 전이 + 유일 제약** | **1건** | **0 / 10회** | **99.0** | **0.0** | 23 ms | 10.7 ms |
| ⑤ 행 잠금 | 1건 | 0 / 10회 | 99.0 | 0.0 | 29 ms | 15.0 ms |

측정 환경: macOS 15.6 (Apple Silicon, 12코어), Java 21, PostgreSQL 17·Redis 7 (Testcontainers).
처리 시간은 참고값이며 방식 간 상대 비교에만 쓴다. `./gradlew comparison`으로 같은 표를 다시 만들 수 있다.

- **① 조회 후 저장**: 확인과 변경 사이에 틈이 있어 매번 중복 승인이 생긴다.
- **② 버전 충돌 감지**: 중복은 막지만, 진 요청은 「이미 처리됨」 대신 오류를 받는다. 오류를 받은 PG는 알림을 다시 보낸다.
- **③ Redis 잠금**: 잠금을 못 얻은 요청이 오류를 받는다. 처리 시간이 TTL보다 길면 잠금이 풀려 중복 승인이 생긴다
  ([재현 테스트](src/test/kotlin/io/github/yjy8501/paymentlab/callback/RedisLockExpiryTest.kt)).
- **④ 조건부 전이 + 유일 제약**: 승인 1건, 나머지는 모두 「이미 처리됨」, 오류 0.
- **⑤ 행 잠금**: ④만큼 정확하지만, 요청들이 잠금을 차례로 기다린다.

왜 ④를 골랐는지는 [ADR 0001](docs/adr/0001-conditional-update-over-distributed-lock.md)에 정리했다.

## 정답 방식: 조건부 상태 전이 + 유일 제약

확인과 변경을 DB 한 문장으로 합친다.

```sql
UPDATE payment
   SET status = 'APPROVED'
 WHERE id = :id
   AND status IN ('PENDING', 'REQUESTING', 'NEEDS_REVIEW')   -- 아직 확정 전일 때만
```

- 바뀐 행이 1이면 이번 요청이 처리한 것 → 같은 트랜잭션에서 승인 기록 저장
- 바뀐 행이 0이면 이미 처리된 것 → 「이미 처리됨」 응답
- 승인 기록 테이블의 `UNIQUE (payment_id)`가 마지막 안전망

구현: [`PaymentSettler`](src/main/kotlin/io/github/yjy8501/paymentlab/payment/PaymentSettler.kt)

## PG 응답을 받지 못했을 때

PG에 승인을 요청하기 전에 결제를 「승인 요청 중」으로 먼저 바꾼다(같은 조건부 전이). 승인 요청이 동시에 여러 번 와도
PG에는 1번만 나간다. PG 호출은 DB 트랜잭션으로 되돌릴 수 없어서, 호출 전에 막아야 한다.

승인 요청에 3초 안에 응답이 없거나 호출이 오류로 끝나면 결과를 모르는 상태로 보고 거래조회로 확정한다.

| 상황 | 결과 |
|---|---|
| 같은 결제에 승인 요청이 동시에 10번 옴 | PG 승인 요청 1번, 나머지 9번은 409 |
| PG 응답이 3초 안에 늦게 옴 | 그 응답으로 확정 |
| PG 호출이 오류로 끝남 | 결과 모름으로 보고 거래조회 |
| 응답은 유실됐지만 PG는 실제로 승인함 | 거래조회 → 승인 확정 |
| 응답이 유실됐고 PG도 승인하지 않음 | 거래조회 → 실패 확정 |
| 대기 직후 1번 + 1초 간격 2번, 총 3번 조회해도 모름 | 「확인 필요」로 남김 (약 5초 안에 응답) |
| 거래조회 도중 같은 결제의 승인 알림이 몰려옴 | 같은 확정 로직을 써서 승인 기록 1건 |

PG는 [가짜 PG](src/main/kotlin/io/github/yjy8501/paymentlab/pg/FakePg.kt)로 재현했다.

## 실행

JDK 21과 Docker만 있으면 된다. 컨테이너는 Testcontainers가 띄운다.

```bash
./gradlew test         # 전체 테스트 (①·③의 중복 재현 테스트도 통과해야 정상)
./gradlew comparison   # 다섯 방식 비교표 → build/reports/comparison.md
```

## 작업 방식

[Spec Kit](https://github.com/github/spec-kit)으로 스펙을 먼저 쓰고, 그 스펙을 기준으로 구현·검증했다.
커밋 히스토리도 이 순서를 따른다.

| 단계 | 결과물 |
|---|---|
| 원칙 | [constitution.md](.specify/memory/constitution.md) — 실제 DB로 경쟁시킨다, 결과는 숫자로 증명한다 등 6개 원칙 |
| 스펙 | [spec.md](specs/001-duplicate-payment-prevention/spec.md) — 사용자 시나리오, 요구사항 FR-001~013, 성공 기준 SC-001~007 |
| 설계 | [plan.md](specs/001-duplicate-payment-prevention/plan.md), [research.md](specs/001-duplicate-payment-prevention/research.md), [data-model.md](specs/001-duplicate-payment-prevention/data-model.md), [API 계약](specs/001-duplicate-payment-prevention/contracts/http-api.md) |
| 작업 목록 | [tasks.md](specs/001-duplicate-payment-prevention/tasks.md) — 63개 작업(스펙 대조로 추가된 10개 포함), 사용자 스토리별로 테스트 먼저 |
| 구현·검증 | 테스트 66개. 스토리마다 실패하는 테스트를 먼저 쓰고 통과시킴 |
| 설계 결정 | [ADR 0001](docs/adr/0001-conditional-update-over-distributed-lock.md), [ADR 0002](docs/adr/0002-separate-unguarded-approval-table.md) |

## 구조

```text
src/main/kotlin/io/github/yjy8501/paymentlab/
├── payment/     결제, 상태 전이, 결과 확정(PaymentSettler)
├── approval/    승인 기록
├── callback/    PG 알림 API와 다섯 가지 처리 방식(strategy/)
├── pg/          가짜 PG, 승인 요청과 거래조회
└── support/     설정, 테스트용 지연·오류 주입 훅
```

## 범위 밖

환불, 부분 취소, 실제 PG 연동, 인증, 화면. 모든 코드는 이 저장소를 위해 새로 작성했다.
