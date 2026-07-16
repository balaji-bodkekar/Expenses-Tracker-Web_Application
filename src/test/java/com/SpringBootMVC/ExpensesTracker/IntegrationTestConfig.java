package com.SpringBootMVC.ExpensesTracker;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Integration Test Configuration
 *
 * <p>Provides a shared MySQL container for integration tests. Use @ContextConfiguration(classes =
 * IntegrationTestConfig.class) in test classes.
 *
 * <p>Or inherit from this class in your test and use @TestcontainersTest.
 */
@Testcontainers
public class IntegrationTestConfig {

  @Container
  static MySQLContainer<?> mysql =
      new MySQLContainer<>("mysql:8.0")
          .withDatabaseName("expenses_test")
          .withUsername("test")
          .withPassword("test")
          .withReuse(false); // Set to true in local dev for faster iteration

  @DynamicPropertySource
  static void configureMySQLProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", mysql::getJdbcUrl);
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
  }
}
