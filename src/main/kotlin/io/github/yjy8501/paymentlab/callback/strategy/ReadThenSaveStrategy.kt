package io.github.yjy8501.paymentlab.callback.strategy

import io.github.yjy8501.paymentlab.approval.UnguardedApproval
import io.github.yjy8501.paymentlab.approval.UnguardedApprovalRepository
import io.github.yjy8501.paymentlab.callback.CallbackRequest
import io.github.yjy8501.paymentlab.callback.CallbackResult
import io.github.yjy8501.paymentlab.callback.CallbackStrategy
import io.github.yjy8501.paymentlab.payment.PaymentRepository
import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.support.ProcessingDelay
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 방식 ① 조회 후 저장. 가장 흔한 실수 패턴이다.
 *
 * 「대기인지 확인」과 「승인으로 변경」이 서로 다른 시점에 일어난다. 여러 요청이 동시에
 * 대기 상태를 읽으면 모두 자기가 처음이라고 판단하고 각자 승인 기록을 남긴다.
 */
@Component
class ReadThenSaveStrategy(
    private val tx: TransactionTemplate,
    private val payments: PaymentRepository,
    private val approvals: UnguardedApprovalRepository,
    private val delay: ProcessingDelay,
) : CallbackStrategy {

    override val key = "read-then-save"

    override fun handle(request: CallbackRequest): CallbackResult = tx.execute {
        val payment = payments.findByPaymentNo(request.paymentNo)
        CallbackResult.precheck(payment?.amount, request)?.let { return@execute it }
        if (!payment!!.status.isOpen) return@execute CallbackResult.ALREADY_PROCESSED

        delay.pause()
        payment.moveTo(request.result.status)
        if (request.result.status == PaymentStatus.APPROVED) {
            approvals.save(UnguardedApproval(payment.id!!, request.pgTxId, payment.amount))
        }
        CallbackResult.applied(request.result)
    }!!
}
