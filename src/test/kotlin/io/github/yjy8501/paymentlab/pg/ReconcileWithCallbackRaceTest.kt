package io.github.yjy8501.paymentlab.pg

import io.github.yjy8501.paymentlab.payment.PaymentStatus
import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

/**
 * US3 시나리오 6: 거래조회로 확정하는 도중 같은 결제의 승인 알림이 몰려와도 승인 기록은 1건이다.
 * 거래조회와 알림 처리가 같은 조건부 전이(PaymentSettler)를 쓰기 때문이다.
 */
class ReconcileWithCallbackRaceTest : IntegrationTest() {

    @Autowired
    private lateinit var fakePg: FakePg

    @AfterEach
    fun reset() = fakePg.reset()

    @Test
    fun `거래조회 중 승인 알림이 동시에 와도 승인 기록은 1건이다`() {
        val paymentNo = createPayment()
        // 응답은 유실, 거래조회는 0.5초 걸리게 해서 조회 도중에 알림이 도착하게 한다
        fakePg.script(paymentNo, FakePg.Reply.NO_REPLY_BUT_APPROVED, inquiryDelay = Duration.ofMillis(500))

        val approval = CompletableFuture.supplyAsync { caller.post("/payments/$paymentNo/approve", "{}") }
        Thread.sleep(3_200)
        val callbacks = caller.postConcurrently(20, "/callbacks/conditional-update", callbackJson(paymentNo))

        assertEquals("APPROVED", approval.get().field("status"))
        assertEquals(PaymentStatus.APPROVED, statusOf(paymentNo))
        assertEquals(1, approvalCount(paymentNo))
        assertEquals(0, callbacks.errorCount)
    }
}
