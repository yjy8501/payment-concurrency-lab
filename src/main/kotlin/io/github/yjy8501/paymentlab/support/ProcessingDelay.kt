package io.github.yjy8501.paymentlab.support

import org.springframework.stereotype.Component
import java.time.Duration

/**
 * 「상태를 읽은 뒤, 쓰기 전」에 일부러 시간을 끄는 훅.
 * 방식 ③에서 처리 시간이 잠금 TTL보다 길어지는 상황을 재현할 때 쓴다 (plan Risks).
 * 기본값은 0이라 운영 흐름에는 영향이 없다.
 */
@Component
class ProcessingDelay {

    @Volatile
    var duration: Duration = Duration.ZERO

    fun pause() {
        if (!duration.isZero) Thread.sleep(duration)
    }

    fun reset() {
        duration = Duration.ZERO
    }
}
