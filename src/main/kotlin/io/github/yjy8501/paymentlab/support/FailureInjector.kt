package io.github.yjy8501.paymentlab.support

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * 테스트에서 지정한 지점에 한 번 예외를 일으키는 훅 (research R10).
 * 기본은 꺼져 있어서 운영 흐름에는 영향이 없다.
 */
@Component
class FailureInjector {

    enum class Point {
        /** 결제 상태를 바꾼 직후, 승인 기록을 저장하기 전 */
        AFTER_STATUS_CHANGE,
    }

    private val armed: MutableSet<Point> = ConcurrentHashMap.newKeySet()

    fun failOnce(point: Point) {
        armed += point
    }

    fun hit(point: Point) {
        if (armed.remove(point)) throw InjectedFailure(point)
    }

    fun reset() = armed.clear()
}

class InjectedFailure(point: FailureInjector.Point) : RuntimeException("injected failure at $point")
