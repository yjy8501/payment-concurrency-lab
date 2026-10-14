package io.github.yjy8501.paymentlab.callback.strategy

import io.github.yjy8501.paymentlab.approval.UnguardedApproval
import io.github.yjy8501.paymentlab.approval.UnguardedApprovalRepository
import io.github.yjy8501.paymentlab.callback.CallbackRequest
import io.github.yjy8501.paymentlab.callback.CallbackResult
import io.github.yjy8501.paymentlab.callback.CallbackStrategy
import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.payment.VersionedPaymentRepository
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 방식 ② 버전 충돌 감지(낙관적 락).
 *
 * ①과 같은 흐름이지만 저장할 때 `WHERE version = ?`로 다른 요청이 먼저 바꿨는지 확인한다.
 * 중복 승인은 막지만, 진 요청은 커밋 단계에서 예외를 받는다. 이 예외는 「이미 처리됨」과
 * 구분되지 않아 오류(409)로 응답한다. 공정 비교를 위해 재시도하지 않는다 (spec Assumptions).
 */
@Component
class OptimisticLockStrategy(
    private val tx: TransactionTemplate,
    private val payments: VersionedPaymentRepository,
    private val approvals: UnguardedApprovalRepository,
) : CallbackStrategy {

    override val key = "optimistic-lock"

    override fun handle(request: CallbackRequest): CallbackResult = try {
        tx.execute {
            val payment = payments.findByPaymentNo(request.paymentNo)
            CallbackResult.precheck(payment?.amount, request)?.let { return@execute it }
            if (!payment!!.status.isOpen) return@execute CallbackResult.ALREADY_PROCESSED

            payment.moveTo(request.result.status)
            if (request.result.status == PaymentStatus.APPROVED) {
                approvals.save(UnguardedApproval(payment.id, request.pgTxId, payment.amount))
            }
            CallbackResult.applied(request.result)
        }!!
    } catch (e: OptimisticLockingFailureException) {
        CallbackResult.CONFLICT
    }
}
