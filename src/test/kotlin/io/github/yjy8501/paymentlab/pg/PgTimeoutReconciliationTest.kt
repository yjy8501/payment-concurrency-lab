package io.github.yjy8501.paymentlab.pg

import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.support.CallResult
import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US3 시나리오 3~5, SC-005: PG 응답이 3초 안에 없으면 거래조회로 확정한다.
 * 대기 직후 1번째 조회, 결과가 없으면 1초 간격으로 2번 더 조회하고, 그래도 모르면 「확인 필요」.
 */
class PgTimeoutReconciliationTest : IntegrationTest() {

    @Autowired
    private lateinit var fakePg: FakePg

    @AfterEach
    fun reset() = fakePg.reset()

    @Test
    fun `응답은 유실됐지만 PG가 실제로 승인했다면 거래조회로 승인을 확정한다`() {
        val paymentNo = createPayment()
        fakePg.script(paymentNo, FakePg.Reply.NO_REPLY_BUT_APPROVED)

        val (response, elapsed) = timed { caller.post("/payments/$paymentNo/approve", "{}") }

        assertEquals("APPROVED", response.field("status"))
        assertEquals("INQUIRY", response.field("resolvedBy"))
        assertEquals(1, approvalCount(paymentNo))
        assertEquals(1, fakePg.inquiryCount(paymentNo), "첫 조회에서 확정된다")
        assertTrue(elapsed >= Duration.ofSeconds(3), "3초는 PG 응답을 기다린다: $elapsed")
    }

    @Test
    fun `PG가 늦게 반영하면 다음 조회에서 확정한다`() {
        val paymentNo = createPayment()
        fakePg.script(
            paymentNo,
            FakePg.Reply.NO_REPLY_BUT_APPROVED,
            inquiries = listOf(InquiryResult.NotFound),
        )

        val response = caller.post("/payments/$paymentNo/approve", "{}")

        assertEquals("APPROVED", response.field("status"))
        assertEquals(2, fakePg.inquiryCount(paymentNo))
        assertEquals(1, approvalCount(paymentNo))
    }

    @Test
    fun `응답이 유실되고 거래조회가 실패를 돌려주면 실패로 확정한다`() {
        val paymentNo = createPayment()
        fakePg.script(
            paymentNo,
            FakePg.Reply.NO_REPLY_NOT_APPROVED,
            inquiries = listOf(InquiryResult.Failed),
        )

        val response = caller.post("/payments/$paymentNo/approve", "{}")

        assertEquals("FAILED", response.field("status"))
        assertEquals("INQUIRY", response.field("resolvedBy"))
        assertEquals(0, approvalCount(paymentNo))
    }

    @Test
    fun `3번 조회해도 결과가 없으면 확인 필요로 두고 약 5초 안에 응답한다`() {
        val paymentNo = createPayment()
        fakePg.script(paymentNo, FakePg.Reply.NO_REPLY_NOT_APPROVED)

        val (response, elapsed) = timed { caller.post("/payments/$paymentNo/approve", "{}") }

        assertEquals("NEEDS_REVIEW", response.field("status"))
        assertEquals("UNRESOLVED", response.field("resolvedBy"))
        assertEquals(PaymentStatus.NEEDS_REVIEW, statusOf(paymentNo), "대기 상태로 남지 않는다")
        assertEquals(3, fakePg.inquiryCount(paymentNo))
        assertTrue(elapsed >= Duration.ofSeconds(5), "대기 3초 + 조회 간격 2초: $elapsed")
        assertTrue(elapsed < Duration.ofSeconds(6), "SC-005: 약 5초 안에 확정: $elapsed")
    }

    @Test
    fun `PG 응답이 3초 안에 늦게 오면 그 응답으로 확정한다`() {
        val paymentNo = createPayment()
        fakePg.script(paymentNo, FakePg.Reply.APPROVE, replyDelay = Duration.ofSeconds(2))

        val (response, elapsed) = timed { caller.post("/payments/$paymentNo/approve", "{}") }

        assertEquals("APPROVED", response.field("status"))
        assertEquals("PG_RESPONSE", response.field("resolvedBy"))
        assertEquals(0, fakePg.inquiryCount(paymentNo), "거래조회는 하지 않는다")
        assertTrue(elapsed >= Duration.ofSeconds(2), "응답을 기다렸다: $elapsed")
    }

    @Test
    fun `PG 응답이 3초를 넘겨 오면 기다리지 않고 거래조회로 확정한다`() {
        val paymentNo = createPayment()
        fakePg.script(paymentNo, FakePg.Reply.APPROVE, replyDelay = Duration.ofSeconds(4))

        val (response, elapsed) = timed { caller.post("/payments/$paymentNo/approve", "{}") }

        assertEquals("APPROVED", response.field("status"))
        assertEquals("INQUIRY", response.field("resolvedBy"))
        assertEquals(1, approvalCount(paymentNo))
        assertTrue(elapsed < Duration.ofSeconds(4), "늦은 응답을 끝까지 기다리지 않는다: $elapsed")
    }

    @Test
    fun `PG 호출이 오류로 끝나면 결과 모름으로 보고 거래조회해서 승인 요청 중에 남기지 않는다`() {
        val paymentNo = createPayment()
        fakePg.script(paymentNo, FakePg.Reply.ERROR)

        val response = caller.post("/payments/$paymentNo/approve", "{}")

        assertEquals(200, response.status, response.body)
        assertEquals("NEEDS_REVIEW", response.field("status"))
        assertEquals("UNRESOLVED", response.field("resolvedBy"))
        assertEquals(PaymentStatus.NEEDS_REVIEW, statusOf(paymentNo), "REQUESTING에 남지 않는다")
        assertEquals(3, fakePg.inquiryCount(paymentNo))
    }

    private fun timed(block: () -> CallResult): Pair<CallResult, Duration> {
        val started = System.nanoTime()
        val result = block()
        return result to Duration.ofNanos(System.nanoTime() - started)
    }
}
