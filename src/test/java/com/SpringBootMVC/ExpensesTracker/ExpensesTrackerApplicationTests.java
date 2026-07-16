package com.SpringBootMVC.ExpensesTracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unit Tests - Fast Path (H2 In-Memory)
 *
 * <p>These tests verify application context loads successfully with H2 database. All tests use the
 * default application.properties (H2) configuration.
 *
 * <p>Tag: 'unit' - Run with: mvn test -Dgroups="unit"
 */
@SpringBootTest
@Tag("unit")
@DisplayName("Expenses Tracker Application Unit Tests")
class ExpensesTrackerApplicationTests {

  @Test
  @DisplayName("Application context should load successfully with H2 database")
  void contextLoads() {
    // Context loads successfully if this test passes
  }
}
