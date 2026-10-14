# HTTP API Contract

실험용 API다. 인증은 없다(spec Assumptions).

## POST /payments — 결제 생성 (FR-001)

요청
```json
{ "amount": 10000 }
```
응답 `201`
```json
{ "paymentNo": "P-...", "amount": 10000, "status": "PENDING" }
```

| 상황 | HTTP |
|---|---|
| 결제 생성 | 201 |
| 금액이 0 이하 (data-model: amount > 0) | 400 |

## POST /callbacks/{strategy} — PG 결과 알림 수신 (FR-003, FR-007)

`strategy`: `read-then-save`(①) · `optimistic-lock`(②) · `redis-lock`(③) · `conditional-update`(④) · `pessimistic-lock`(⑤)

요청
```json
{ "paymentNo": "P-...", "pgTxId": "T-...", "result": "APPROVED", "amount": 10000 }
```

| 상황 | HTTP | body.result |
|---|---|---|
| 이번 요청이 승인을 반영함 | 200 | `APPROVED` |
| 이번 요청이 실패를 반영함 | 200 | `FAILED` |
| 이미 최종 상태 | 200 | `ALREADY_PROCESSED` |
| 결제 없음 | 404 | `PAYMENT_NOT_FOUND` |
| 금액 불일치 | 422 | `AMOUNT_MISMATCH` |
| 버전 충돌(②) · 잠금 획득 실패(③) | 409 | `CONFLICT` |
| 알 수 없는 `strategy` | 400 | — |
| 그 밖의 오류 | 500 | — |

측정 시 「오류 응답」은 HTTP 4xx(404·422 제외)·5xx를 센다(SC-002).

## POST /payments/{paymentNo}/approve — PG 승인 요청 (US3, FR-009~011)

시스템이 결제를 「승인 요청 중」(`REQUESTING`)으로 먼저 바꾼 뒤 가짜 PG에 승인을 요청한다. 이 변경에 성공한 요청만 PG를 호출한다(FR-013). 응답이 3초 안에 없거나 호출이 오류로 끝나면 바로 거래조회를 하고, 결과가 없으면
1초 간격으로 2번 더(총 3번) 조회해 확정한 뒤 응답한다. 최악의 경우 약 5초가 걸린다.

응답 `200`
```json
{ "paymentNo": "P-...", "status": "APPROVED", "resolvedBy": "PG_RESPONSE" }
```
`status`: `APPROVED` · `FAILED` · `NEEDS_REVIEW`

`resolvedBy`: `PG_RESPONSE`(바로 응답받음) · `INQUIRY`(거래조회로 확정) · `UNRESOLVED`(확인 필요)

| 상황 | HTTP | body.result |
|---|---|---|
| 결과 확정 (승인·실패·확인 필요) | 200 | — (위 응답 형식) |
| 결제 없음 | 404 | `PAYMENT_NOT_FOUND` |
| 결제가 대기 상태가 아님 (다른 요청이 이미 승인 요청 중이거나 확정됨). PG는 호출하지 않는다 | 409 | `ALREADY_REQUESTED` |
