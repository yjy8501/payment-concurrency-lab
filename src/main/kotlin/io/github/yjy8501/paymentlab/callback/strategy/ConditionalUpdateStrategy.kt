package io.github.yjy8501.paymentlab.callback.strategy

import io.github.yjy8501.paymentlab.callback.CallbackRequest
import io.github.yjy8501.paymentlab.callback.CallbackResult
import io.github.yjy8501.paymentlab.callback.CallbackStrategy
import io.github.yjy8501.paymentlab.payment.PaymentRepository
import io.github.yjy8501.paymentlab.payment.PaymentSettler
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

/**
 * 방식 ④ 조건부 상태 전이 + 승인 1건 제약.
 *
 * 상태 확인과 변경을 `UPDATE ... WHERE status IN (대기, 확인 필요)` 한 문장으로 합친다.
 * 바뀐 행이 1이면 이번 요청이 처리한 것이고, 0이면 다른 요청이 이미 처리한 것이다.
 * 나머지 요청은 오류가 아니라 「이미 처리됨」을 받는다.
 */
@Component
class ConditionalUpdateStrategy(
    private val tx: TransactionTemplate,
    private val payments: PaymentRepository,
    private val settler: PaymentSettler,
) : CallbackStrategy {

    override val key = "conditional-update"

    override fun handle(request: CallbackRequest): CallbackResult = tx.execute {
        val payment = payments.findByPaymentNo(request.paymentNo)
        CallbackResult.precheck(payment?.amount, request)?.let { return@execute it }

        if (settler.settle(payment!!, request.result.status, request.pgTxId)) {
            CallbackResult.applied(request.result)
        } else {
            CallbackResult.ALREADY_PROCESSED
        }
    }!!
}
