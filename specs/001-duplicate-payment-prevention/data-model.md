# Data Model: 이중 결제 방지 실험

## payment (결제)

| 열 | 타입 | 규칙 |
|---|---|---|
| id | bigint, PK | 자동 증가 |
| payment_no | varchar(40), UNIQUE | 외부에 노출되는 결제 번호. PG 알림이 이 값으로 결제를 찾는다 |
| amount | bigint | 원 단위, 0보다 커야 한다 |
| status | varchar(20) | `PENDING` · `APPROVED` · `FAILED` · `NEEDS_REVIEW` |
| version | bigint | 방식 ②에서만 사용(`VersionedPayment`) |
| created_at / updated_at | timestamptz | |

엔티티 매핑: `Payment`(①③④⑤, US3), `VersionedPayment`(②). 같은 테이블을 가리킨다(research R5).

### 상태 전이 (FR-002)

```text
PENDING ──승인──▶ APPROVED
   │  └──실패──▶ FAILED
   └──조회 3회 실패──▶ NEEDS_REVIEW ──승인──▶ APPROVED
                              └──실패──▶ FAILED
```

- `APPROVED`, `FAILED`는 최종 상태다. 최종 상태에 도착한 알림은 「이미 처리됨」으로 응답한다.
- 방식 ④와 US3의 상태 변경은 조건부 UPDATE로만 한다:
  `... WHERE id = ? AND status IN ('PENDING', 'NEEDS_REVIEW')`

## approval (승인 기록, 방식 ④ · US3)

| 열 | 타입 | 규칙 |
|---|---|---|
| id | bigint, PK | |
| payment_id | bigint, FK → payment.id, **UNIQUE** | 결제당 최대 1건 (FR-004) |
| pg_tx_id | varchar(40) | PG 거래 번호 |
| amount | bigint | 승인 금액 |
| approved_at | timestamptz | |

## approval_unguarded (승인 기록, 방식 ①②③⑤)

`approval`과 열 구성이 같고, `payment_id`에 유일 제약이 **없다**. 중복 승인이 몇 번 일어났는지 세기 위한 실험용 테이블이다(research R4).

## 데이터베이스에 저장하지 않는 값

- **PG 알림**: `paymentNo`, `pgTxId`, `result`(`APPROVED` · `FAILED`), `amount`
- **거래조회 결과**: `APPROVED(pgTxId)` · `FAILED` · `NOT_FOUND`(결과 없음)
- **콜백 처리 결과**: `APPROVED` · `FAILED` · `ALREADY_PROCESSED` · `PAYMENT_NOT_FOUND` · `AMOUNT_MISMATCH` · `CONFLICT`

## 검증 규칙

- 알림 금액 ≠ 결제 금액 → 상태를 바꾸지 않고 `AMOUNT_MISMATCH`
- 결제 번호가 없음 → `PAYMENT_NOT_FOUND`
- 상태 변경과 승인 기록 저장은 한 트랜잭션에서 처리한다(FR-006)
