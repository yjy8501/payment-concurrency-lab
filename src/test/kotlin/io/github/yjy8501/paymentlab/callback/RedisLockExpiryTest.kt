package io.github.yjy8501.paymentlab.callback

import io.github.yjy8501.paymentlab.callback.strategy.RedisLockStrategy
import io.github.yjy8501.paymentlab.support.CallResult
import io.github.yjy8501.paymentlab.support.IntegrationTest
import io.github.yjy8501.paymentlab.support.ProcessingDelay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

/**
 * US2 시나리오 4, SC-004: 방식 ③(만료 잠금)은 처리 시간이 잠금 TTL보다 길면 중복 승인이 생긴다.
 *
 * 1번 요청이 처리하는 도중(0.3초) 잠금이 먼저 풀리고(0.1초), 그 사이 PG가 다시 보낸
 * 2번 요청이 잠금을 새로 얻어 같은 결제를 또 승인한다.
 */
class RedisLockExpiryTest : IntegrationTest() {

    @Autowired
    private lateinit var redisLock: RedisLockStrategy

    @Autowired
    private lateinit var delay: ProcessingDelay

    private val path = "/callbacks/redis-lock"

    @AfterEach
    fun reset() {
        redisLock.resetTtl()
        delay.reset()
    }

    @Test
    fun `처리 시간이 잠금 TTL보다 길면 중복 승인이 생긴다`() {
        redisLock.ttl = Duration.ofMillis(100)
        delay.duration = Duration.ofMillis(300)
        val paymentNo = createPayment()

        val (first, second) = sendTwice(paymentNo, gap = Duration.ofMillis(150))

        assertEquals("APPROVED", first.result)
        assertEquals("APPROVED", second.result)
        assertEquals(2, approvalCount(paymentNo), "잠금이 먼저 풀려 같은 결제가 두 번 승인된다")
    }

    @Test
    fun `대조군 - 잠금 TTL이 처리 시간보다 길면 두 번째 요청은 잠금을 얻지 못한다`() {
        delay.duration = Duration.ofMillis(300)
        val paymentNo = createPayment()

        val (first, second) = sendTwice(paymentNo, gap = Duration.ofMillis(150))

        assertEquals("APPROVED", first.result)
        assertEquals("CONFLICT", second.result)
        assertEquals(1, approvalCount(paymentNo))
    }

    /** 같은 알림을 [gap] 간격으로 두 번 보낸다. PG가 응답을 못 받아 다시 보내는 상황이다 */
    private fun sendTwice(paymentNo: String, gap: Duration): Pair<CallResult, CallResult> {
        val first = CompletableFuture.supplyAsync { caller.post(path, callbackJson(paymentNo)) }
        Thread.sleep(gap)
        val second = caller.post(path, callbackJson(paymentNo))
        return first.get() to second
    }
}
