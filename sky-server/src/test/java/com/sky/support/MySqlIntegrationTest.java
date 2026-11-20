package com.sky.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Paths;

/**
 * Base class for tests that run the real application against MySQL 8.0 in Docker.
 * <p>
 * The database is initialized from docker/mysql/init, the same scripts docker compose uses,
 * so these tests also check every migration. One container is shared by all test classes.
 * Without a Docker engine the tests are skipped.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers(disabledWithoutDocker = true)
public abstract class MySqlIntegrationTest {

    // Maven runs the tests from the sky-server directory.
    private static final String INIT_SCRIPTS = Paths.get("..", "docker", "mysql", "init").toAbsolutePath().normalize().toString();

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("sky_take_out")
            .withCopyFileToContainer(MountableFile.forHostPath(INIT_SCRIPTS), "/docker-entrypoint-initdb.d/");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.druid.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.druid.username", MYSQL::getUsername);
        registry.add("spring.datasource.druid.password", MYSQL::getPassword);
    }
}
