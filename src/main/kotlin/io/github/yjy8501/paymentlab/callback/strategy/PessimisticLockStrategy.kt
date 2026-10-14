package io.github.yjy8501.paymentlab.callback.strategy

import io.github.yjy8501.paymentlab.approval.UnguardedApproval
import io.github.yjy8501.paymentlab.approval.UnguardedApprovalRepository
import io.github.yjy8501.paymentlab.callback.CallbackRequest
import io.github.yjy8501.paymentlab.callback.CallbackResult
import io.github.yjy8501.paymentlab.callback.CallbackStrategy
import io.github.yjy8501.paymentlab.payment.PaymentRepository
import io.github.yjy8501.paymentlab.payment.PaymentStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 방식 ⑤ 행 잠금(비관적 락).
 *
 * 결제를 읽을 때 행을 잠그므로, 다른 요청은 앞 요청이 커밋할 때까지 기다렸다가 확정된 상태를 읽는다.
 * 승인은 정확히 1번이지만, 요청들이 잠금을 차례로 기다리는 시간이 생긴다.
 * 유일 제약이 없는 테이블에 기록해 잠금만으로 1건이 지켜지는지 확인한다 (research R3).
 */
@Component
class PessimisticLockStrategy(
    private val tx: TransactionTemplate,
    private val payments: PaymentRepository,
    private val approvals: UnguardedApprovalRepository,
) : CallbackStrategy {

    override val key = "pessimistic-lock"

    override fun handle(request: CallbackRequest): CallbackResult = tx.execute {
        val payment = payments.findByPaymentNoForUpdate(request.paymentNo)
        CallbackResult.precheck(payment?.amount, request)?.let { return@execute it }
        if (!payment!!.status.isOpen) return@execute CallbackResult.ALREADY_PROCESSED

        payment.moveTo(request.result.status)
        if (request.result.status == PaymentStatus.APPROVED) {
            approvals.save(UnguardedApproval(payment.id!!, request.pgTxId, payment.amount))
        }
        CallbackResult.applied(request.result)
    }!!
}
