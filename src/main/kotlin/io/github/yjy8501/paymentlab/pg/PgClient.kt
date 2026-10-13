package io.github.yjy8501.paymentlab.pg

/** PG 승인 요청의 응답 */
sealed interface PgApproveResult {
    data class Approved(val pgTxId: String) : PgApproveResult
    data object Failed : PgApproveResult
}

/** PG 거래조회 결과 */
sealed interface InquiryResult {
    data class Approved(val pgTxId: String) : InquiryResult
    data object Failed : InquiryResult

    /** PG도 아직 결과를 모른다 */
    data object NotFound : InquiryResult
}

/** PG 호출이 응답 없이 오류로 끝났다. 승인됐는지 알 수 없다 */
class PgCallException(message: String) : RuntimeException(message)

/** 결제대행사(PG)와의 통신. 이 저장소에서는 [FakePg]만 구현한다 (원칙 V) */
interface PgClient {

    /** 승인 요청. 응답이 늦거나 오지 않을 수 있다 */
    fun approve(paymentNo: String, amount: Long): PgApproveResult

    fun inquire(paymentNo: String): InquiryResult
}
