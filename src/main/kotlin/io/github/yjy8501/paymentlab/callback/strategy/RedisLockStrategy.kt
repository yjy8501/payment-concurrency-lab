package io.github.yjy8501.paymentlab.callback.strategy

import io.github.yjy8501.paymentlab.callback.CallbackRequest
import io.github.yjy8501.paymentlab.callback.CallbackResult
import io.github.yjy8501.paymentlab.callback.CallbackStrategy
import io.github.yjy8501.paymentlab.support.LabProperties
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 방식 ③ 만료 시간이 있는 외부 잠금(Redis) + 방식 ①.
 *
 * 잠금을 얻은 요청만 ① 흐름을 실행하고, 잠금을 못 얻은 요청은 기다리지 않고 오류(409)를 받는다.
 * 잠금은 트랜잭션이 커밋된 뒤에 푼다. 다만 처리 시간이 TTL보다 길면 처리 도중 잠금이 풀리고,
 * 그 사이 들어온 요청이 같은 결제를 또 승인할 수 있다.
 */
@Component
class RedisLockStrategy(
    private val lock: ExpiringLock,
    private val readThenSave: ReadThenSaveStrategy,
    private val properties: LabProperties,
) : CallbackStrategy {

    override val key = "redis-lock"

    /** 잠금 유지 시간. 만료 실험에서 테스트가 바꿀 수 있게 열어 둔다 */
    @Volatile
    var ttl: Duration = properties.redisLock.ttl

    fun resetTtl() {
        ttl = properties.redisLock.ttl
    }

    override fun handle(request: CallbackRequest): CallbackResult {
        val lockKey = "lock:payment:${request.paymentNo}"
        val token = lock.tryAcquire(lockKey, ttl) ?: return CallbackResult.CONFLICT
        try {
            // readThenSave.handle은 자체 트랜잭션을 커밋한 뒤 돌아오므로, 잠금은 커밋 이후에 풀린다
            return readThenSave.handle(request)
        } finally {
            lock.release(lockKey, token)
        }
    }
}
