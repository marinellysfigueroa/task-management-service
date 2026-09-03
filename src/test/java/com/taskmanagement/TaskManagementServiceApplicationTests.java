package com.taskmanagement;

import com.taskmanagement.integration.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

class TaskManagementServiceApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies the Spring application context starts successfully with
        // a real PostgreSQL container wired in via Testcontainers.
    }
}
