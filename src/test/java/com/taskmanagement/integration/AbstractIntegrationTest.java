package com.taskmanagement.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for integration tests that need a real PostgreSQL instance.
 * A single container is started once and reused across all subclasses to
 * keep the test suite fast.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("task_management_test")
                    .withUsername("test_user")
                    .withPassword("test_password");

    static {
        // Singleton container pattern: started once per JVM from a static
        // initializer, and deliberately never stopped.
        //
        // The previous @BeforeAll/@AfterAll pair looked symmetric but could not
        // work with more than one test class. Spring caches the ApplicationContext
        // across classes that share this configuration, while @DynamicPropertySource
        // is evaluated only when that context is first created. Stopping the
        // container in @AfterAll therefore left the cached DataSource pointing at a
        // destroyed container (the restart gets a fresh random port), and every
        // class after the first failed to connect.
        //
        // Ryuk, Testcontainers' reaper sidecar, removes the container when the JVM
        // exits, so skipping the explicit stop() leaks nothing.
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
    }
}
