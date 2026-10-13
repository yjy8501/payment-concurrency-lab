package io.github.yjy8501.paymentlab.payment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

enum class PaymentStatus {
    PENDING,

    /** PG에 승인 요청을 보낼 권리를 선점한 상태 (FR-013) */
    REQUESTING,
    APPROVED,
    FAILED,
    NEEDS_REVIEW;

    /** 아직 승인·실패로 확정되지 않은 상태 */
    val isOpen: Boolean
        get() = this == PENDING || this == REQUESTING || this == NEEDS_REVIEW

    /** FR-002에서 허용한 전이만 true */
    fun canMoveTo(target: PaymentStatus): Boolean = when (target) {
        APPROVED, FAILED -> isOpen
        REQUESTING -> this == PENDING
        NEEDS_REVIEW -> this == REQUESTING
        PENDING -> false
    }

    companion object {
        val OPEN: List<PaymentStatus> = entries.filter { it.isOpen }
    }
}

/**
 * 버전 검사 없이 `payment` 테이블을 다루는 엔티티. 방식 ①③④⑤와 PG 승인 흐름이 쓴다.
 * 방식 ②는 같은 테이블을 [VersionedPayment]로 다룬다 (research R5).
 */
@Entity
@Table(name = "payment")
class Payment(
    paymentNo: String,
    amount: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "payment_no", nullable = false, updatable = false)
    val paymentNo: String = paymentNo

    @Column(nullable = false, updatable = false)
    val amount: Long = amount

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: PaymentStatus = PaymentStatus.PENDING
        protected set

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now()

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    init {
        require(amount > 0) { "결제 금액은 0보다 커야 한다: $amount" }
    }

    fun moveTo(target: PaymentStatus) {
        check(status.canMoveTo(target)) { "허용되지 않은 상태 전이: $status -> $target" }
        status = target
        updatedAt = Instant.now()
    }
}
