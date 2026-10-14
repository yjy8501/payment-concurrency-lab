package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.RepeatedTest
import kotlin.test.assertEquals

/**
 * US2 시나리오 5, SC-006: 방식 ⑤(행 잠금)도 승인을 정확히 1번만 반영한다.
 * 유일 제약이 없는 테이블에 기록하므로, 1건은 잠금만으로 지켜진 결과다.
 */
class PessimisticLockConcurrencyTest : IntegrationTest() {

    @RepeatedTest(10, name = "{currentRepetition}/{totalRepetitions}회")
    fun `같은 승인 알림 100개가 동시에 와도 승인 기록은 1건이다`() {
        val paymentNo = createPayment()

        val stats = caller.postConcurrently(100, "/callbacks/pessimistic-lock", callbackJson(paymentNo))

        assertEquals(1, approvalCount(paymentNo), "승인 기록 수")
        assertEquals(1, stats.count("APPROVED"))
        assertEquals(99, stats.count("ALREADY_PROCESSED"))
        assertEquals(0, stats.errorCount)
    }
}
