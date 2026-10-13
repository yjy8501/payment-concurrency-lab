package io.github.yjy8501.paymentlab.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 테스트마다 실제 PostgreSQL·Redis 컨테이너를 띄운다 (원칙 I).
 * 같은 설정을 쓰는 테스트들은 Spring 테스트 컨텍스트 캐시 덕분에 컨테이너를 함께 쓴다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestContainersConfig {

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer =
        PostgreSQLContainer(DockerImageName.parse("postgres:17"))

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> =
        GenericContainer(DockerImageName.parse("redis:7")).withExposedPorts(6379)
}
