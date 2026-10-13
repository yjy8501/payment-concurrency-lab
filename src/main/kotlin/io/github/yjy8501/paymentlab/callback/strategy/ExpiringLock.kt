package io.github.yjy8501.paymentlab.callback.strategy

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/**
 * 만료 시간이 있는 Redis 잠금의 최소 구현 (research R3).
 *
 * 잠금 연장(watchdog) 같은 보완 기능을 일부러 넣지 않았다. 이 실험은 「TTL이 지나면
 * 잠금이 풀린다」는 성질 자체가 어떤 문제를 만드는지 보여주려는 것이다.
 */
@Component
class ExpiringLock(private val redis: StringRedisTemplate) {

    /** 잠금을 얻으면 토큰을, 이미 누가 잡고 있으면 null을 돌려준다 (`SET key token NX PX ttl`) */
    fun tryAcquire(key: String, ttl: Duration): String? {
        val token = UUID.randomUUID().toString()
        return if (redis.opsForValue().setIfAbsent(key, token, ttl) == true) token else null
    }

    /**
     * 내 토큰일 때만 지운다. TTL이 지나 다른 요청이 잠금을 새로 잡았다면 그 잠금은 건드리지 않는다.
     * 확인과 삭제를 Lua 스크립트 하나로 실행해 그 사이에 끼어들 틈을 없앤다.
     */
    fun release(key: String, token: String) {
        redis.execute(RELEASE, listOf(key), token)
    }

    private companion object {
        val RELEASE: RedisScript<Long> = RedisScript.of(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """.trimIndent(),
            Long::class.java,
        )
    }
}
