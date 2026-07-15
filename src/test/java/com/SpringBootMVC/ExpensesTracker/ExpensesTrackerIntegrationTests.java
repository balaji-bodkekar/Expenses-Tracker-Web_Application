package com.SpringBootMVC.ExpensesTracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration Tests - Containerized MySQL (Testcontainers)
 * 
 * These tests verify application context with actual MySQL database behavior.
 * Uses Testcontainers to spin up a real MySQL instance for each test class.
 * 
 * Tag: 'integration' - Run with: mvn test -Dgroups="integration"
 * Run locally: mvn verify (includes both unit and integration tests)
 */
@SpringBootTest
@Testcontainers
@Import(IntegrationTestConfig.class)
@Tag("integration")
@DisplayName("Expenses Tracker Application Integration Tests")
class ExpensesTrackerIntegrationTests {

    @Test
    @DisplayName("Application context should load successfully with Testcontainers MySQL")
    void contextLoadsWithMySQL() {
        assertNotNull(this);
    }

    // Add your integration tests here
    // Example:
    // @Test
    // void shouldSaveAndRetrieveExpense() {
    //     // Test actual MySQL behavior
    // }
}
