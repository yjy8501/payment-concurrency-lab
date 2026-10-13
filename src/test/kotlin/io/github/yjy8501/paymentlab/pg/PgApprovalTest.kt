package io.github.yjy8501.paymentlab.pg

import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

/** US3 시나리오 1·2: PG가 바로 응답하면 그 결과로 확정한다 */
class PgApprovalTest : IntegrationTest() {

    @Autowired
    private lateinit var fakePg: FakePg

    @AfterEach
    fun reset() = fakePg.reset()

    @Test
    fun `PG가 바로 승인하면 승인으로 확정한다`() {
        val paymentNo = createPayment()
        fakePg.script(paymentNo, FakePg.Reply.APPROVE)

        val response = caller.post("/payments/$paymentNo/approve", "{}")

        assertEquals(200, response.status)
        assertEquals("APPROVED", response.field("status"))
        assertEquals("PG_RESPONSE", response.field("resolvedBy"))
        assertEquals(PaymentStatus.APPROVED, statusOf(paymentNo))
        assertEquals(1, approvalCount(paymentNo))
    }

    @Test
    fun `PG가 바로 실패를 돌려주면 실패로 확정한다`() {
        val paymentNo = createPayment()
        fakePg.script(paymentNo, FakePg.Reply.FAIL)

        val response = caller.post("/payments/$paymentNo/approve", "{}")

        assertEquals("FAILED", response.field("status"))
        assertEquals("PG_RESPONSE", response.field("resolvedBy"))
        assertEquals(0, approvalCount(paymentNo))
    }

    @Test
    fun `대기 상태가 아닌 결제는 다시 승인 요청할 수 없다`() {
        val paymentNo = createPayment()
        caller.post("/payments/$paymentNo/approve", "{}")

        val again = caller.post("/payments/$paymentNo/approve", "{}")

        assertEquals(409, again.status)
        assertEquals(1, approvalCount(paymentNo))
    }
}
