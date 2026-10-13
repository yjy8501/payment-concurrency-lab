package io.github.yjy8501.paymentlab

import io.github.yjy8501.paymentlab.support.TestContainersConfig
import org.springframework.boot.fromApplication
import org.springframework.boot.with

/** `./gradlew bootTestRun`으로 컨테이너와 함께 애플리케이션을 띄운다 */
fun main(args: Array<String>) {
    fromApplication<PaymentLabApplication>().with(TestContainersConfig::class).run(*args)
}
