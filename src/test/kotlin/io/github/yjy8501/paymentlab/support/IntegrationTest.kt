package io.github.yjy8501.paymentlab.support

import io.github.yjy8501.paymentlab.payment.PaymentStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

/**
 * 실제 서버를 임의 포트로 띄우고 HTTP로 요청하는 통합 테스트의 기반 (research R6).
 * 모든 테스트가 같은 설정을 써서 컨테이너와 서버를 함께 쓴다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestContainersConfig::class)
abstract class IntegrationTest {

    @Value("\${local.server.port}")
    private var port: Int = 0

    @Autowired
    protected lateinit var jdbc: JdbcTemplate

    protected val caller: ConcurrentCaller by lazy { ConcurrentCaller("http://localhost:$port") }

    protected fun createPayment(amount: Long = 10_000): String {
        val response = caller.post("/payments", """{"amount": $amount}""")
        assertEquals(201, response.status, response.body)
        return PAYMENT_NO_PATTERN.find(response.body)!!.groupValues[1]
    }

    protected fun callbackJson(
        paymentNo: String,
        result: String = "APPROVED",
        amount: Long = 10_000,
        pgTxId: String = "T-$paymentNo",
    ): String = """{"paymentNo":"$paymentNo","pgTxId":"$pgTxId","result":"$result","amount":$amount}"""

    protected fun statusOf(paymentNo: String): PaymentStatus =
        PaymentStatus.valueOf(
            jdbc.queryForObject("select status from payment where payment_no = ?", String::class.java, paymentNo)!!,
        )

    /** 두 승인 기록 테이블(approval, approval_unguarded)을 합친 승인 기록 수 */
    protected fun approvalCount(paymentNo: String): Int =
        jdbc.queryForObject(
            """
            select (select count(*) from approval a where a.payment_id = p.id)
                 + (select count(*) from approval_unguarded u where u.payment_id = p.id)
              from payment p
             where p.payment_no = ?
            """,
            Int::class.java,
            paymentNo,
        )!!

    private companion object {
        val PAYMENT_NO_PATTERN = Regex("\"paymentNo\"\\s*:\\s*\"([^\"]+)\"")
    }
}
