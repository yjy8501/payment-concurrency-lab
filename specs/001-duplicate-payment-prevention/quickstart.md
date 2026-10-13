# Quickstart: 이중 결제 방지 실험 검증

## 준비물

- JDK 21
- Docker (Testcontainers가 PostgreSQL·Redis 컨테이너를 자동으로 띄운다)

## 1. 전체 테스트

```bash
./gradlew test
```

기대 결과: 모든 테스트 통과. 방식 ①·③의 「중복이 생긴다」는 테스트도 **통과**해야 한다(중복을 재현했다는 뜻).

## 2. 다섯 방식 비교표 만들기 (SC-003)

```bash
./gradlew comparison
cat build/reports/comparison.md
```

기대 결과: 방식별로 10번 실행한 결과가 표로 나온다.

| 방식 | 승인 기록 수 (10회) | 이미 처리됨 | 오류 | 처리 시간 |
|---|---|---|---|---|
| ① 조회 후 저장 | 1회 이상 2건 이상 | — | — | 측정값 |
| ② 버전 충돌 감지 | 매번 1건 | 일부 | 1개 이상 | 측정값 |
| ③ 만료 잠금 | 매번 1건(정상 TTL) | 일부 | 1개 이상 | 측정값 |
| ④ 조건부 전이 + 유일 제약 | 매번 1건 | 매번 99 | 매번 0 | 측정값 |
| ⑤ 행 잠금 | 매번 1건 | 매번 99 | 매번 0 | 측정값 |

처리 시간(전체·평균)은 실행한 컴퓨터에 따라 달라지는 참고값이다.
③의 TTL 만료 중복은 별도 테스트(`RedisLockExpiryTest`)에서 확인한다.

## 3. 애플리케이션 직접 띄우기 (선택)

```bash
./gradlew bootTestRun
```

`TestPaymentLabApplication`이 PostgreSQL·Redis 컨테이너와 함께 애플리케이션을 띄운다.
[contracts/http-api.md](./contracts/http-api.md)의 API를 직접 호출해 볼 수 있다.

## 4. 시나리오별 확인

| 확인할 것 | 테스트 | spec |
|---|---|---|
| 100개 동시 알림 → 승인 1건 | `ConditionalUpdateConcurrencyTest` | US1, SC-001·002 |
| ①에서 중복 발생 | `ReadThenSaveConcurrencyTest` | US2-2, SC-004 |
| ② 승인 1건 + 오류 발생 | `OptimisticLockConcurrencyTest` | US2-3 |
| ③ TTL 만료 시 중복 | `RedisLockExpiryTest` | US2-4, SC-004 |
| ③ 기본 TTL에서 승인 1건 + 잠금 실패 오류 | `RedisLockConcurrencyTest` | US2 표 ③ |
| ⑤ 승인 1건, 오류 0개 | `PessimisticLockConcurrencyTest` | US2-5, SC-006 |
| PG가 바로 승인·실패 응답, 동시 승인 요청은 PG 1번만 | `PgApprovalTest` | US3-1·2·7, SC-007 |
| 응답 지연·유실·호출 오류 → 거래조회로 확정 | `PgTimeoutReconciliationTest` | US3-3~5, SC-005, FR-012 |
| 거래조회 중 승인 알림 동시 도착 | `ReconcileWithCallbackRaceTest` | US3-6 |
| 처리 중간 오류 → 반쪽 반영 없음 | `AtomicityTest` | FR-006 |
| 예외 상황(금액 불일치, 없는 결제, 승인·실패 동시 도착) | `CallbackEdgeCaseTest` | Edge Cases |

참고 문서: [data-model.md](./data-model.md), [contracts/http-api.md](./contracts/http-api.md)
