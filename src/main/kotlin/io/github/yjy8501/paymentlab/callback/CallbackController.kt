package io.github.yjy8501.paymentlab.callback

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** PG 결과 알림 수신. 경로의 `{strategy}`로 처리 방식을 고른다 (FR-007) */
@RestController
class CallbackController(strategies: List<CallbackStrategy>) {

    private val strategies: Map<String, CallbackStrategy> = strategies.associateBy { it.key }

    @PostMapping("/callbacks/{strategy}")
    fun receive(
        @PathVariable strategy: String,
        @RequestBody request: CallbackRequest,
    ): ResponseEntity<CallbackResponse> {
        val handler = strategies[strategy]
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "unknown strategy: $strategy")
        val result = handler.handle(request)
        return ResponseEntity.status(result.httpStatus).body(CallbackResponse(result))
    }
}
