package io.github.yjy8501.paymentlab.support

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** 보낼 요청 하나 */
data class Call(val path: String, val json: String)

/** HTTP 응답 하나 */
data class CallResult(val status: Int, val body: String, val elapsedNanos: Long) {

    /** 응답 body의 "result" 값. 없으면 null */
    val result: String? = field("result")

    /** 응답 body에서 문자열 필드 하나를 꺼낸다. 없으면 null */
    fun field(name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"(\\w+)\"").find(body)?.groupValues?.get(1)
}

/** 동시 요청 묶음의 결과 */
data class CallStats(val calls: List<CallResult>, val totalNanos: Long) {

    fun count(result: String): Int = calls.count { it.result == result }

    /** contracts/http-api.md 기준 오류 응답: 404·422를 제외한 4xx와 5xx */
    val errorCount: Int
        get() = calls.count { it.status >= 400 && it.status != 404 && it.status != 422 }

    val totalMillis: Long
        get() = TimeUnit.NANOSECONDS.toMillis(totalNanos)

    val averageMillis: Double
        get() = calls.map { it.elapsedNanos }.average() / 1_000_000.0
}

/**
 * 요청 N개를 [CountDownLatch]로 묶어 동시에 출발시킨다.
 * 모든 스레드가 준비된 뒤 한꺼번에 출발해야 실제로 DB에서 경쟁이 일어난다.
 */
class ConcurrentCaller(private val baseUrl: String) {

    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun post(path: String, json: String): CallResult {
        val request = HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build()
        val started = System.nanoTime()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        return CallResult(response.statusCode(), response.body(), System.nanoTime() - started)
    }

    fun postConcurrently(count: Int, path: String, json: String): CallStats =
        postConcurrently(List(count) { Call(path, json) })

    /** 서로 다른 요청을 섞어서 동시에 보낸다 (예: 승인 알림과 실패 알림) */
    fun postConcurrently(calls: List<Call>): CallStats {
        val executor = Executors.newFixedThreadPool(calls.size)
        val ready = CountDownLatch(calls.size)
        val start = CountDownLatch(1)
        try {
            val futures = calls.map { call ->
                executor.submit<CallResult> {
                    ready.countDown()
                    start.await()
                    post(call.path, call.json)
                }
            }
            ready.await()
            val started = System.nanoTime()
            start.countDown()
            val results = futures.map { it.get(60, TimeUnit.SECONDS) }
            return CallStats(results, System.nanoTime() - started)
        } finally {
            executor.shutdownNow()
        }
    }
}
