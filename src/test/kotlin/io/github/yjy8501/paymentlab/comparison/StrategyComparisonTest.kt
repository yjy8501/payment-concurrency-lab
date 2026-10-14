package io.github.yjy8501.paymentlab.comparison

import io.github.yjy8501.paymentlab.support.CallStats
import io.github.yjy8501.paymentlab.support.IntegrationTest
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import kotlin.test.assertTrue

/**
 * US2, SC-003: 다섯 방식을 같은 조건(대기 결제 1건, 동시 알림 100개)으로 10번씩 실행하고
 * 결과를 `build/reports/comparison.md`에 표로 남긴다 (research R9).
 *
 * 실행: `./gradlew comparison`
 */
@Tag("comparison")
class StrategyComparisonTest : IntegrationTest() {

    private data class Strategy(val key: String, val label: String)

    private data class Run(val approvals: Int, val stats: CallStats)

    private val strategies = listOf(
        Strategy("read-then-save", "① 조회 후 저장"),
        Strategy("optimistic-lock", "② 버전 충돌 감지"),
        Strategy("redis-lock", "③ 만료 잠금 (Redis)"),
        Strategy("conditional-update", "④ 조건부 전이 + 유일 제약"),
        Strategy("pessimistic-lock", "⑤ 행 잠금"),
    )

    @Test
    fun `다섯 방식 비교표를 만든다`() {
        // JVM·커넥션 풀 예열. 첫 실행이 느려서 결과가 흔들리는 것을 막는다
        strategies.forEach { runOnce(it) }

        val results = strategies.associateWith { strategy -> (1..REPETITIONS).map { runOnce(strategy) } }

        val report = render(results)
        File("build/reports").mkdirs()
        File("build/reports/comparison.md").writeText(report)
        println(report)

        // 비교표의 숫자가 spec의 예상 결과(US2 표)와 맞는지 함께 확인한다
        fun runsOf(key: String) = results.getValue(strategies.first { it.key == key })
        listOf("conditional-update", "pessimistic-lock").forEach { key ->
            assertTrue(runsOf(key).all { it.approvals == 1 && it.stats.errorCount == 0 }, "$key: ${runsOf(key).map { it.approvals }}")
        }
        listOf("optimistic-lock", "redis-lock").forEach { key ->
            assertTrue(runsOf(key).all { it.approvals == 1 && it.stats.count("CONFLICT") >= 1 }, "$key: ${runsOf(key).map { it.approvals }}")
        }
    }

    private fun runOnce(strategy: Strategy): Run {
        val paymentNo = createPayment()
        val stats = caller.postConcurrently(CONCURRENCY, "/callbacks/${strategy.key}", callbackJson(paymentNo))
        return Run(approvalCount(paymentNo), stats)
    }

    private fun render(results: Map<Strategy, List<Run>>): String = buildString {
        appendLine("# 처리 방식 비교")
        appendLine()
        appendLine("같은 결제 1건에 같은 승인 알림 ${CONCURRENCY}개를 동시에 보내는 실험을 방식마다 ${REPETITIONS}번 반복했다.")
        appendLine()
        appendLine("| 방식 | 승인 기록 수 | 중복 승인 발생 | 이미 처리됨 (평균) | 오류 (평균) | 전체 처리 시간 (평균) | 평균 응답 시간 |")
        appendLine("|---|---|---|---|---|---|---|")
        results.forEach { (strategy, runs) ->
            val approvals = runs.map { it.approvals }
            val duplicated = approvals.count { it >= 2 }
            appendLine(
                "| ${strategy.label}" +
                    " | ${range(approvals)}" +
                    " | $duplicated / ${runs.size}회" +
                    " | ${"%.1f".format(runs.map { it.stats.count("ALREADY_PROCESSED") }.average())}" +
                    " | ${"%.1f".format(runs.map { it.stats.errorCount }.average())}" +
                    " | ${"%.0f".format(runs.map { it.stats.totalMillis }.average())} ms" +
                    " | ${"%.1f".format(runs.map { it.stats.averageMillis }.average())} ms |",
            )
        }
        appendLine()
        appendLine("- 승인 기록 수: ${REPETITIONS}번 중 최소~최대. 정답은 매번 1건")
        appendLine("- 오류: 404·422를 제외한 4xx·5xx 응답 (②는 버전 충돌, ③은 잠금 획득 실패로 409)")
        appendLine("- 처리 시간은 아래 환경에서 잰 참고값이며, 방식 간 상대 비교에만 쓴다")
        appendLine()
        appendLine("측정 환경: ${environment()}")
    }

    private fun range(values: List<Int>): String =
        if (values.min() == values.max()) "${values.min()}건" else "${values.min()}~${values.max()}건"

    private fun environment(): String {
        val os = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"
        val cpus = Runtime.getRuntime().availableProcessors()
        val java = System.getProperty("java.version")
        return "$os, CPU $cpus 코어, Java $java, PostgreSQL 17·Redis 7 (Testcontainers), ${LocalDate.now()}"
    }

    private companion object {
        const val CONCURRENCY = 100
        const val REPETITIONS = 10
    }
}
