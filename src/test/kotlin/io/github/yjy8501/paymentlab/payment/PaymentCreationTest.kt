package io.github.yjy8501.paymentlab.payment

import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PaymentCreationTest : IntegrationTest() {

    @Test
    fun `결제를 만들면 대기 상태이고 승인 기록은 없다`() {
        val paymentNo = createPayment(amount = 10_000)

        assertEquals(PaymentStatus.PENDING, statusOf(paymentNo))
        assertEquals(0, approvalCount(paymentNo))
    }

    @Test
    fun `금액이 0 이하면 결제를 만들지 않는다`() {
        val response = caller.post("/payments", """{"amount": 0}""")

        assertEquals(400, response.status)
    }
}
