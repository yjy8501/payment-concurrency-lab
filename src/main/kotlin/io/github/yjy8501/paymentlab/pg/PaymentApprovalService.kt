package io.github.yjy8501.paymentlab.pg

import io.github.yjy8501.paymentlab.payment.Payment
import io.github.yjy8501.paymentlab.payment.PaymentRepository
import io.github.yjy8501.paymentlab.payment.PaymentSettler
import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.support.LabProperties
import jakarta.annotation.PreDestroy
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** 결제 결과를 어떻게 확정했는지 */
enum class ResolvedBy {
    /** PG 응답으로 바로 확정 */
    PG_RESPONSE,

    /** 응답을 못 받아 거래조회로 확정 */
    INQUIRY,

    /** 거래조회로도 확정하지 못해 「확인 필요」로 둠 */
    UNRESOLVED,
}

data class ApprovalOutcome(val paymentNo: String, val status: PaymentStatus, val resolvedBy: ResolvedBy)

class PaymentNotFoundException(paymentNo: String) : RuntimeException("payment not found: $paymentNo")

class PaymentNotPendingException(paymentNo: String, status: PaymentStatus) :
    RuntimeException("payment $paymentNo is $status, not PENDING")

/**
 * PG 승인 요청과, 응답을 못 받았을 때의 거래조회 (US3, research R8).
 *
 * 결과 확정은 콜백 방식 ④와 같은 [PaymentSettler]로 한다. 그래서 거래조회와 PG 알림이
 * 동시에 같은 결제를 확정하려 해도 승인 기록은 1건이다.
 */
@Service
class PaymentApprovalService(
    private val tx: TransactionTemplate,
    private val payments: PaymentRepository,
    private val settler: PaymentSettler,
    private val pg: PgClient,
    properties: LabProperties,
) {
    private val settings = properties.pg

    /** PG 호출 전용 스레드. 응답이 오지 않는 호출은 타임아웃 후 인터럽트로 끊는다 */
    private val pgExecutor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    fun approve(paymentNo: String): ApprovalOutcome {
        val payment = payments.findByPaymentNo(paymentNo) ?: throw PaymentNotFoundException(paymentNo)
        // 상태를 읽고 판단하면 동시 요청이 모두 통과한다. 선점에 성공한 요청만 PG를 호출한다 (FR-013)
        val claimed = tx.execute { settler.claimForRequest(payment) }!!
        if (!claimed) throw PaymentNotPendingException(paymentNo, payments.findByPaymentNo(paymentNo)!!.status)

        val resolvedBy = when (val reply = requestApproval(payment)) {
            is PgApproveResult.Approved -> settle(payment, PaymentStatus.APPROVED, reply.pgTxId).let { ResolvedBy.PG_RESPONSE }
            PgApproveResult.Failed -> settle(payment, PaymentStatus.FAILED, null).let { ResolvedBy.PG_RESPONSE }
            null -> reconcile(payment)
        }

        // 확정 도중 PG 알림이 먼저 반영됐을 수 있으므로 DB의 최종 상태를 돌려준다
        val status = payments.findByPaymentNo(paymentNo)!!.status
        return ApprovalOutcome(paymentNo, status, resolvedBy)
    }

    /**
     * 응답 대기 시간 안에 답이 오면 그 결과를, 아니면 null(결과 모름)을 돌려준다 (FR-009).
     * 호출이 오류로 끝난 경우도 PG가 승인했는지 알 수 없으므로 결과 모름으로 본다.
     */
    private fun requestApproval(payment: Payment): PgApproveResult? {
        val call = pgExecutor.submit<PgApproveResult> { pg.approve(payment.paymentNo, payment.amount) }
        return try {
            call.get(settings.responseTimeout.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            call.cancel(true)
            null
        } catch (e: ExecutionException) {
            null
        }
    }

    /** 대기 직후 1번, 이후 간격을 두고 최대 횟수까지 거래조회한다 (FR-010, FR-011) */
    private fun reconcile(payment: Payment): ResolvedBy {
        repeat(settings.inquiryAttempts) { attempt ->
            if (attempt > 0) Thread.sleep(settings.inquiryInterval)
            when (val result = pg.inquire(payment.paymentNo)) {
                is InquiryResult.Approved -> {
                    settle(payment, PaymentStatus.APPROVED, result.pgTxId)
                    return ResolvedBy.INQUIRY
                }
                InquiryResult.Failed -> {
                    settle(payment, PaymentStatus.FAILED, null)
                    return ResolvedBy.INQUIRY
                }
                InquiryResult.NotFound -> Unit
            }
        }
        tx.executeWithoutResult { settler.markNeedsReview(payment) }
        return ResolvedBy.UNRESOLVED
    }

    private fun settle(payment: Payment, target: PaymentStatus, pgTxId: String?) {
        tx.executeWithoutResult { settler.settle(payment, target, pgTxId) }
    }

    @PreDestroy
    fun shutdown() {
        pgExecutor.shutdownNow()
    }
}
