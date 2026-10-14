package io.github.yjy8501.paymentlab.support

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

/** application.yml의 `lab.*` 설정 */
@ConfigurationProperties("lab")
data class LabProperties(
    val pg: Pg,
    val redisLock: RedisLock,
) {
    data class Pg(
        val responseTimeout: Duration,
        val inquiryInterval: Duration,
        val inquiryAttempts: Int,
    )

    data class RedisLock(val ttl: Duration)
}
