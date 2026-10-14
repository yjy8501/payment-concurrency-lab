package io.github.yjy8501.paymentlab.payment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/**
 * `payment` 테이블을 낙관적 락(`@Version`)으로 다루는 엔티티. 방식 ②만 쓴다 (research R5).
 * 결제 생성은 [Payment]가 하므로 이 엔티티는 조회와 상태 변경만 한다.
 */
@Entity(name = "VersionedPayment")
@Table(name = "payment")
class VersionedPayment protected constructor() {

    @Id
    val id: Long = 0

    @Column(name = "payment_no", updatable = false)
    val paymentNo: String = ""

    @Column(updatable = false)
    val amount: Long = 0

    @Enumerated(EnumType.STRING)
    var status: PaymentStatus = PaymentStatus.PENDING
        protected set

    @Version
    var version: Long = 0
        protected set

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.EPOCH
        protected set

    fun moveTo(target: PaymentStatus) {
        check(status.canMoveTo(target)) { "허용되지 않은 상태 전이: $status -> $target" }
        status = target
        updatedAt = Instant.now()
    }
}

interface VersionedPaymentRepository : JpaRepository<VersionedPayment, Long> {

    fun findByPaymentNo(paymentNo: String): VersionedPayment?
}
