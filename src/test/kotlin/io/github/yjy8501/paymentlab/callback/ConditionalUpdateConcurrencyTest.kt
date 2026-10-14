package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.RepeatedTest
import kotlin.test.assertEquals

/** US1, SC-001·002: 방식 ④는 같은 승인 알림 100개를 동시에 받아도 승인을 정확히 1번만 반영한다 */
class ConditionalUpdateConcurrencyTest : IntegrationTest() {

    @RepeatedTest(10, name = "{currentRepetition}/{totalRepetitions}회")
    fun `같은 승인 알림 100개가 동시에 와도 승인 기록은 1건이다`() {
        val paymentNo = createPayment()

        val stats = caller.postConcurrently(100, "/callbacks/conditional-update", callbackJson(paymentNo))

        assertEquals(PaymentStatus.APPROVED, statusOf(paymentNo))
        assertEquals(1, approvalCount(paymentNo), "승인 기록 수")
        assertEquals(1, stats.count("APPROVED"), "승인 완료 응답 수")
        assertEquals(99, stats.count("ALREADY_PROCESSED"), "이미 처리됨 응답 수")
        assertEquals(0, stats.errorCount, "오류 응답 수")
    }
}
