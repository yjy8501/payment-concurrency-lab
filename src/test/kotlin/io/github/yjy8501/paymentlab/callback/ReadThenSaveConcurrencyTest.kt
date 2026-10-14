package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * US2 시나리오 2, SC-004: 방식 ①(조회 후 저장)은 동시 알림에서 승인 기록이 2건 이상 생길 수 있다.
 * 경쟁은 확률적이므로 10번 중 1번 이상 중복이 관찰되면 재현 성공으로 본다 (plan Risks).
 */
class ReadThenSaveConcurrencyTest : IntegrationTest() {

    @Test
    fun `같은 승인 알림 100개가 동시에 오면 중복 승인이 생긴다`() {
        val approvalsPerRun = (1..10).map {
            val paymentNo = createPayment()
            caller.postConcurrently(100, "/callbacks/read-then-save", callbackJson(paymentNo))
            approvalCount(paymentNo)
        }

        println("방식 ① 회차별 승인 기록 수: $approvalsPerRun")
        assertTrue(approvalsPerRun.any { it >= 2 }, "10번 중 한 번도 중복 승인이 재현되지 않았다: $approvalsPerRun")
    }
}
