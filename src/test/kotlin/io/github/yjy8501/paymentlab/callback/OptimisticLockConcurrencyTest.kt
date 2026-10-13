package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.RepeatedTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** US2 시나리오 3: 방식 ②(버전 충돌 감지)는 승인 1건을 지키지만, 진 요청은 오류(409)를 받는다 */
class OptimisticLockConcurrencyTest : IntegrationTest() {

    @RepeatedTest(10, name = "{currentRepetition}/{totalRepetitions}회")
    fun `승인 기록은 1건이지만 충돌한 요청은 오류로 응답한다`() {
        val paymentNo = createPayment()

        val stats = caller.postConcurrently(100, "/callbacks/optimistic-lock", callbackJson(paymentNo))

        assertEquals(1, approvalCount(paymentNo), "승인 기록 수")
        assertEquals(1, stats.count("APPROVED"))
        assertTrue(stats.count("CONFLICT") >= 1, "충돌(409) 응답이 1개 이상이어야 한다")
        assertEquals(stats.count("CONFLICT"), stats.errorCount)
    }
}
