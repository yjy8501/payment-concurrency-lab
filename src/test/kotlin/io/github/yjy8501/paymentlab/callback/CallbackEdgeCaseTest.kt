package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.support.Call
import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US1 시나리오 3과 spec Edge Cases (방식 ④) */
class CallbackEdgeCaseTest : IntegrationTest() {

    private val path = "/callbacks/conditional-update"

    @Test
    fun `이미 승인된 결제에 같은 알림이 다시 오면 이미 처리됨으로 응답한다`() {
        val paymentNo = createPayment()
        caller.post(path, callbackJson(paymentNo))

        val again = caller.post(path, callbackJson(paymentNo))

        assertEquals(200, again.status)
        assertEquals("ALREADY_PROCESSED", again.result)
        assertEquals(1, approvalCount(paymentNo))
    }

    @Test
    fun `없는 결제의 알림에는 결제 없음으로 응답한다`() {
        val response = caller.post(path, callbackJson("P-does-not-exist"))

        assertEquals(404, response.status)
        assertEquals("PAYMENT_NOT_FOUND", response.result)
    }

    @Test
    fun `금액이 다르면 승인하지 않는다`() {
        val paymentNo = createPayment(amount = 10_000)

        val response = caller.post(path, callbackJson(paymentNo, amount = 9_000))

        assertEquals(422, response.status)
        assertEquals("AMOUNT_MISMATCH", response.result)
        assertEquals(PaymentStatus.PENDING, statusOf(paymentNo))
        assertEquals(0, approvalCount(paymentNo))
    }

    @Test
    fun `승인과 실패 알림이 동시에 오면 하나만 반영한다`() {
        val paymentNo = createPayment()
        val calls = List(50) { Call(path, callbackJson(paymentNo, result = "APPROVED")) } +
            List(50) { Call(path, callbackJson(paymentNo, result = "FAILED")) }

        val stats = caller.postConcurrently(calls.shuffled())

        assertEquals(1, stats.count("APPROVED") + stats.count("FAILED"), "반영된 알림 수")
        assertEquals(99, stats.count("ALREADY_PROCESSED"))
        val status = statusOf(paymentNo)
        assertTrue(status == PaymentStatus.APPROVED || status == PaymentStatus.FAILED)
        assertEquals(if (status == PaymentStatus.APPROVED) 1 else 0, approvalCount(paymentNo))
    }

    @Test
    fun `확인 필요 상태인 결제에 승인 알림이 오면 승인으로 확정한다`() {
        val paymentNo = createPayment()
        jdbc.update("update payment set status = 'NEEDS_REVIEW' where payment_no = ?", paymentNo)

        val response = caller.post(path, callbackJson(paymentNo))

        assertEquals("APPROVED", response.result)
        assertEquals(PaymentStatus.APPROVED, statusOf(paymentNo))
        assertEquals(1, approvalCount(paymentNo))
    }
}
