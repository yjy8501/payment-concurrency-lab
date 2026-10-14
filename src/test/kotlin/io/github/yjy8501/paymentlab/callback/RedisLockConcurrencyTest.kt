package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.RepeatedTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * US2 표 ③: 기본 TTL(5초)에서는 처리가 TTL 안에 끝나므로 승인 1건을 지킨다.
 * 대신 잠금을 얻지 못한 요청은 기다리지 않고 오류(409)를 받는다.
 * TTL이 처리 시간보다 짧을 때의 중복은 RedisLockExpiryTest에서 확인한다.
 */
class RedisLockConcurrencyTest : IntegrationTest() {

    @RepeatedTest(10, name = "{currentRepetition}/{totalRepetitions}회")
    fun `기본 TTL에서는 승인 1건이지만 잠금을 못 얻은 요청은 오류로 응답한다`() {
        val paymentNo = createPayment()

        val stats = caller.postConcurrently(100, "/callbacks/redis-lock", callbackJson(paymentNo))

        assertEquals(1, approvalCount(paymentNo), "승인 기록 수")
        assertEquals(1, stats.count("APPROVED"))
        assertTrue(stats.count("CONFLICT") >= 1, "잠금 획득 실패(409) 응답이 1개 이상이어야 한다")
        assertEquals(stats.count("CONFLICT"), stats.errorCount)
    }
}
