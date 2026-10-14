package io.github.yjy8501.paymentlab

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PaymentLabApplication

fun main(args: Array<String>) {
    runApplication<PaymentLabApplication>(*args)
}
