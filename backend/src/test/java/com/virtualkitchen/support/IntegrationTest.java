package com.virtualkitchen.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Base for integration tests: one real Postgres and one S3-compatible (RustFS) container shared by every test
 * class (started once per JVM) so the cached Spring context stays valid across classes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String S3_ACCESS_KEY = "test-access-key";
    private static final String S3_SECRET_KEY = "test-secret-key-0123456789";

    /** Same S3-compatible server as infra/docker-compose.yml (RustFS), pinned to a release. */
    static final GenericContainer<?> S3 = new GenericContainer<>("rustfs/rustfs:1.0.0")
            .withEnv("RUSTFS_ACCESS_KEY", S3_ACCESS_KEY)
            .withEnv("RUSTFS_SECRET_KEY", S3_SECRET_KEY)
            .withEnv("RUSTFS_VOLUMES", "/data")
            .withEnv("RUSTFS_ADDRESS", "0.0.0.0:9000")
            .withExposedPorts(9000)
            .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
        S3.start();
    }

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.endpoint", () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(9000));
        registry.add("app.storage.access-key", () -> S3_ACCESS_KEY);
        registry.add("app.storage.secret-key", () -> S3_SECRET_KEY);
    }
}
