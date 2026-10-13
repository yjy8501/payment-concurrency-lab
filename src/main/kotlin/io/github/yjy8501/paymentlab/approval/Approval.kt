package io.github.yjy8501.paymentlab.approval

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

@MappedSuperclass
abstract class ApprovalRecord(
    paymentId: Long,
    pgTxId: String,
    amount: Long,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null

    @Column(name = "payment_id", nullable = false, updatable = false)
    val paymentId: Long = paymentId

    @Column(name = "pg_tx_id", nullable = false, updatable = false)
    val pgTxId: String = pgTxId

    @Column(nullable = false, updatable = false)
    val amount: Long = amount

    @Column(name = "approved_at", nullable = false, updatable = false)
    val approvedAt: Instant = Instant.now()
}

/** 결제당 최대 1건만 허용되는 승인 기록 (방식 ④, PG 승인 흐름) */
@Entity
@Table(name = "approval")
class Approval(paymentId: Long, pgTxId: String, amount: Long) : ApprovalRecord(paymentId, pgTxId, amount)

/** 유일 제약이 없는 승인 기록 (방식 ①②③⑤). 중복 승인을 세기 위한 실험용이다 */
@Entity
@Table(name = "approval_unguarded")
class UnguardedApproval(paymentId: Long, pgTxId: String, amount: Long) : ApprovalRecord(paymentId, pgTxId, amount)

interface ApprovalRepository : JpaRepository<Approval, Long>

interface UnguardedApprovalRepository : JpaRepository<UnguardedApproval, Long>
