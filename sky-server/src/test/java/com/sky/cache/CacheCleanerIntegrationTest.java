package com.sky.cache;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs against Redis 7 in Docker, the version docker compose uses. */
@Testcontainers(disabledWithoutDocker = true)
class CacheCleanerIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private RedisTemplate<String, String> redisTemplate;
    private final CacheCleaner cleaner = new CacheCleaner();

    @BeforeEach
    void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new StringRedisSerializer());
        redisTemplate.afterPropertiesSet();
        redisTemplate.execute(connection -> {
            connection.flushAll();
            return null;
        }, true);
        ReflectionTestUtils.setField(cleaner, "redisTemplate", redisTemplate);
    }

    @AfterEach
    void disconnect() {
        connectionFactory.destroy();
    }

    @Test
    void deletesEveryMatchingKeyAcrossSeveralScanBatchesAndNothingElse() {
        // More keys than one SCAN batch (100), so the cursor has to continue.
        for (int category = 1; category <= 250; category++) {
            redisTemplate.opsForValue().set("dish_" + category, "[]");
        }
        redisTemplate.opsForValue().set("setmealCache::1", "[]");
        redisTemplate.opsForValue().set("shop_status", "1");

        assertEquals(250, cleaner.deleteByPattern("dish_*"));

        Set<String> left = redisTemplate.keys("*");
        assertEquals(2, left.size(), left.toString());
        assertTrue(left.contains("setmealCache::1"));
        assertTrue(left.contains("shop_status"));
    }

    @Test
    void aPatternWithNoMatchesDeletesNothing() {
        redisTemplate.opsForValue().set("shop_status", "1");

        assertEquals(0, cleaner.deleteByPattern("setmealCache::*"));
        assertEquals(1, redisTemplate.keys("*").size());
    }
}
