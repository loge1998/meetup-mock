package common

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import java.time.Duration

object TestContainerRunner {

  @Container
  val postgreSqlContainer: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:latest").apply {
    withDatabaseName("testdb")
    withUsername("testuser")
    withPassword("testpass")
    withMinimumRunningDuration(Duration.ofSeconds(5))
  }

  @DynamicPropertySource
  @JvmStatic
  fun registerDynamicProperties(registry: DynamicPropertyRegistry) {
    registry.add("spring.datasource.url", postgreSqlContainer::getJdbcUrl)
    registry.add("spring.datasource.driverName", postgreSqlContainer::getDriverClassName)
    registry.add("spring.datasource.username", postgreSqlContainer::getUsername)
    registry.add("spring.datasource.password", postgreSqlContainer::getPassword)
  }

  fun startPostgresIfNotRunning() {
    if (!postgreSqlContainer.isRunning) {
      postgreSqlContainer.start()
    }
  }
}