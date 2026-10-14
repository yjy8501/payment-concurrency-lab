-- 승인 요청 중(REQUESTING): PG에 승인 요청을 보낼 권리를 선점한 상태 (FR-013)
alter table payment drop constraint payment_status_check;

alter table payment
    add constraint payment_status_check
        check (status in ('PENDING', 'REQUESTING', 'APPROVED', 'FAILED', 'NEEDS_REVIEW'));
