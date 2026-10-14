package io.github.yjy8501.paymentlab.payment

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface PaymentRepository : JpaRepository<Payment, Long> {

    fun findByPaymentNo(paymentNo: String): Payment?

    /** 방식 ⑤: 결제 행을 잠가서 읽는다 (`SELECT ... FOR UPDATE`). 다른 요청은 커밋까지 기다린다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentNo = :paymentNo")
    fun findByPaymentNoForUpdate(@Param("paymentNo") paymentNo: String): Payment?

    /**
     * 조건부 상태 전이. 현재 상태가 [from] 중 하나일 때만 [to]로 바꾸고, 바뀐 행 수(0 또는 1)를 돌려준다.
     *
     * 같은 행을 바꾸려는 다른 UPDATE가 있으면 PostgreSQL은 그 트랜잭션이 끝날 때까지 기다린 뒤
     * WHERE 조건을 다시 확인한다. 그래서 동시에 몇 개가 와도 1을 받는 요청은 하나뿐이다 (research R3).
     */
    @Modifying
    @Query(
        """
        update Payment p
           set p.status = :to, p.updatedAt = :now
         where p.id = :id
           and p.status in :from
        """,
    )
    fun transition(
        @Param("id") id: Long,
        @Param("from") from: Collection<PaymentStatus>,
        @Param("to") to: PaymentStatus,
        @Param("now") now: Instant = Instant.now(),
    ): Int
}
