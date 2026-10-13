package io.github.yjy8501.paymentlab.payment

import io.github.yjy8501.paymentlab.approval.Approval
import io.github.yjy8501.paymentlab.approval.ApprovalRepository
import io.github.yjy8501.paymentlab.support.FailureInjector
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 결과를 확정하는 단일 지점. 방식 ④와 PG 승인 흐름(US3)이 함께 쓴다.
 *
 * 「아직 확정 전이면 바꾼다」를 조건부 UPDATE 한 문장으로 처리하고, 승인 기록은
 * 결제당 유일 제약이 걸린 `approval`에 저장한다. 둘 다 호출한 쪽의 트랜잭션 안에서 실행된다.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class PaymentSettler(
    private val payments: PaymentRepository,
    private val approvals: ApprovalRepository,
    private val failureInjector: FailureInjector,
) {

    /**
     * [payment]를 [target]으로 확정한다. 이번 호출이 상태를 바꿨으면 true,
     * 다른 요청이 먼저 확정했으면 false.
     */
    fun settle(payment: Payment, target: PaymentStatus, pgTxId: String?): Boolean {
        require(target == PaymentStatus.APPROVED || target == PaymentStatus.FAILED) { "확정 상태가 아니다: $target" }

        val paymentId = payment.id!!
        val changed = payments.transition(paymentId, PaymentStatus.OPEN, target) == 1
        if (!changed) return false

        failureInjector.hit(FailureInjector.Point.AFTER_STATUS_CHANGE)
        if (target == PaymentStatus.APPROVED) {
            approvals.save(Approval(paymentId, requireNotNull(pgTxId) { "승인에는 PG 거래 번호가 필요하다" }, payment.amount))
        }
        return true
    }

    /** 거래조회로도 확정하지 못한 결제를 「확인 필요」로 바꾼다 (FR-011) */
    fun markNeedsReview(payment: Payment): Boolean =
        payments.transition(payment.id!!, listOf(PaymentStatus.PENDING), PaymentStatus.NEEDS_REVIEW) == 1
}
