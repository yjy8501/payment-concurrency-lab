package io.github.yjy8501.paymentlab.pg

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/** PG 승인 요청 API (contracts/http-api.md) */
@RestController
class PaymentApprovalController(private val approvalService: PaymentApprovalService) {

    @PostMapping("/payments/{paymentNo}/approve")
    fun approve(@PathVariable paymentNo: String): ApprovalOutcome = approvalService.approve(paymentNo)

    @ExceptionHandler(PaymentNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun notFound(e: PaymentNotFoundException): Map<String, String?> = mapOf("message" to e.message)

    @ExceptionHandler(PaymentNotPendingException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun notPending(e: PaymentNotPendingException): Map<String, String?> = mapOf("message" to e.message)
}
