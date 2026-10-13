package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.support.FailureInjector
import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/** FR-006: 상태 변경과 승인 기록은 함께 반영되거나 함께 취소된다 (research R10) */
class AtomicityTest : IntegrationTest() {

    @Autowired
    private lateinit var failureInjector: FailureInjector

    @AfterEach
    fun reset() = failureInjector.reset()

    @Test
    fun `상태를 바꾼 뒤 승인 기록 저장 전에 실패하면 둘 다 반영되지 않는다`() {
        val paymentNo = createPayment()
        failureInjector.failOnce(FailureInjector.Point.AFTER_STATUS_CHANGE)

        val failed = caller.post("/callbacks/conditional-update", callbackJson(paymentNo))

        assertEquals(500, failed.status)
        assertEquals(PaymentStatus.PENDING, statusOf(paymentNo), "상태 변경도 취소되어야 한다")
        assertEquals(0, approvalCount(paymentNo))

        val retried = caller.post("/callbacks/conditional-update", callbackJson(paymentNo))

        assertEquals("APPROVED", retried.result, "PG가 다시 보낸 알림은 정상 처리된다")
        assertEquals(1, approvalCount(paymentNo))
    }
}
