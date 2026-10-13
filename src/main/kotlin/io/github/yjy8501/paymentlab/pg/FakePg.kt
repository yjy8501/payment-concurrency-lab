package io.github.yjy8501.paymentlab.pg

import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * 테스트에서 결제마다 응답 방식을 정할 수 있는 가짜 PG (FR-012, research R7).
 *
 * 「응답은 유실됐지만 PG 내부에서는 승인됨」을 기억해 두었다가 거래조회 때 그 결과를 돌려준다.
 * 시나리오를 정하지 않은 결제는 바로 승인한다.
 */
@Component
class FakePg : PgClient {

    enum class Reply {
        /** 바로 승인 응답 */
        APPROVE,

        /** 바로 실패 응답 */
        FAIL,

        /** 승인은 했지만 응답이 오지 않음 */
        NO_REPLY_BUT_APPROVED,

        /** 승인하지 않았고 응답도 오지 않음 */
        NO_REPLY_NOT_APPROVED,

        /** 호출이 오류로 끝남 (연결 끊김 등). 승인하지 않음 */
        ERROR,
    }

    private class Script(
        val reply: Reply,
        val replyDelay: Duration,
        val inquiries: ConcurrentLinkedDeque<InquiryResult>,
        val inquiryDelay: Duration,
    )

    private val scripts = ConcurrentHashMap<String, Script>()

    /** PG 내부에서 실제로 승인된 거래 (결제 번호 → PG 거래 번호) */
    private val approvedTransactions = ConcurrentHashMap<String, String>()

    private val inquiryCounts = ConcurrentHashMap<String, AtomicInteger>()

    private val approveCounts = ConcurrentHashMap<String, AtomicInteger>()

    /**
     * [paymentNo]의 응답 방식을 정한다.
     * [replyDelay]는 승인·실패 응답이 도착하기까지 걸리는 시간이다. 승인은 지연 전에 PG 내부에서 이미 처리된다.
     * [inquiries]는 거래조회 때 앞에서부터 하나씩 돌려줄 결과다. 다 쓰면 PG 내부 상태대로 답한다.
     */
    fun script(
        paymentNo: String,
        reply: Reply,
        replyDelay: Duration = Duration.ZERO,
        inquiries: List<InquiryResult> = emptyList(),
        inquiryDelay: Duration = Duration.ZERO,
    ) {
        scripts[paymentNo] = Script(reply, replyDelay, ConcurrentLinkedDeque(inquiries), inquiryDelay)
    }

    fun inquiryCount(paymentNo: String): Int = inquiryCounts[paymentNo]?.get() ?: 0

    /** PG가 받은 승인 요청 횟수 */
    fun approveCount(paymentNo: String): Int = approveCounts[paymentNo]?.get() ?: 0

    fun reset() {
        scripts.clear()
        approvedTransactions.clear()
        inquiryCounts.clear()
        approveCounts.clear()
    }

    override fun approve(paymentNo: String, amount: Long): PgApproveResult {
        approveCounts.computeIfAbsent(paymentNo) { AtomicInteger() }.incrementAndGet()
        val script = scripts[paymentNo]
        val replyDelay = script?.replyDelay ?: Duration.ZERO
        return when (script?.reply ?: Reply.APPROVE) {
            Reply.APPROVE -> {
                val pgTxId = recordApproval(paymentNo)
                sleep(replyDelay)
                PgApproveResult.Approved(pgTxId)
            }
            Reply.FAIL -> {
                sleep(replyDelay)
                PgApproveResult.Failed
            }
            Reply.ERROR -> throw PgCallException("PG 연결이 끊겼다: $paymentNo")
            Reply.NO_REPLY_BUT_APPROVED -> {
                recordApproval(paymentNo)
                neverReply()
            }
            Reply.NO_REPLY_NOT_APPROVED -> neverReply()
        }
    }

    override fun inquire(paymentNo: String): InquiryResult {
        inquiryCounts.computeIfAbsent(paymentNo) { AtomicInteger() }.incrementAndGet()
        val script = scripts[paymentNo]
        sleep(script?.inquiryDelay ?: Duration.ZERO)

        script?.inquiries?.pollFirst()?.let { return it }
        return approvedTransactions[paymentNo]?.let { InquiryResult.Approved(it) } ?: InquiryResult.NotFound
    }

    private fun recordApproval(paymentNo: String): String =
        approvedTransactions.computeIfAbsent(paymentNo) { "T-" + UUID.randomUUID().toString().take(8) }

    private fun sleep(duration: Duration) {
        if (!duration.isZero) Thread.sleep(duration)
    }

    /** 응답이 오지 않는 상황. 호출한 쪽이 타임아웃 후 스레드를 인터럽트하면 끝난다 */
    private fun neverReply(): Nothing {
        Thread.sleep(Duration.ofMinutes(1))
        error("PG가 응답하지 않았다")
    }
}
