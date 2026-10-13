package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.payment.PaymentStatus
import org.springframework.http.HttpStatus

/** PG가 알려 준 결제 결과 */
enum class PgOutcome(val status: PaymentStatus) {
    APPROVED(PaymentStatus.APPROVED),
    FAILED(PaymentStatus.FAILED),
}

/** PG 결과 알림 (contracts/http-api.md) */
data class CallbackRequest(
    val paymentNo: String,
    val pgTxId: String,
    val result: PgOutcome,
    val amount: Long,
)

/** 알림 처리 결과와 HTTP 상태 (contracts/http-api.md 표) */
enum class CallbackResult(val httpStatus: HttpStatus) {
    APPROVED(HttpStatus.OK),
    FAILED(HttpStatus.OK),
    ALREADY_PROCESSED(HttpStatus.OK),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT),
    CONFLICT(HttpStatus.CONFLICT),
    ;

    companion object {
        /** 이번 요청이 결제 상태를 바꿨을 때의 결과 */
        fun applied(outcome: PgOutcome): CallbackResult = when (outcome) {
            PgOutcome.APPROVED -> APPROVED
            PgOutcome.FAILED -> FAILED
        }

        /** 모든 방식이 공통으로 하는 사전 검사. 통과하면 null */
        fun precheck(paymentAmount: Long?, request: CallbackRequest): CallbackResult? = when {
            paymentAmount == null -> PAYMENT_NOT_FOUND
            paymentAmount != request.amount -> AMOUNT_MISMATCH
            else -> null
        }
    }
}

data class CallbackResponse(val result: CallbackResult)

/** 같은 알림을 처리하는 다섯 가지 방식의 공통 인터페이스 (FR-007) */
interface CallbackStrategy {

    /** API 경로에 쓰는 이름. 예: `conditional-update` */
    val key: String

    fun handle(request: CallbackRequest): CallbackResult
}
