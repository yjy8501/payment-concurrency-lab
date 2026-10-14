package io.github.yjy8501.paymentlab.payment

import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

data class CreatePaymentRequest(val amount: Long)

data class PaymentResponse(val paymentNo: String, val amount: Long, val status: PaymentStatus)

@RestController
class PaymentController(private val payments: PaymentRepository) {

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun create(@RequestBody request: CreatePaymentRequest): PaymentResponse {
        if (request.amount <= 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be positive")
        }
        val payment = payments.save(Payment(paymentNo = newPaymentNo(), amount = request.amount))
        return PaymentResponse(payment.paymentNo, payment.amount, payment.status)
    }

    private fun newPaymentNo(): String = "P-" + UUID.randomUUID().toString().replace("-", "").take(20)
}
