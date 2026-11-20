package com.sky.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deletes cached menu entries by key pattern.
 * <p>
 * KEYS walks the entire keyspace in a single command, and Redis runs one command at a time, so
 * every other client waits until it finishes. SCAN visits the keys in small batches instead,
 * and other requests are served in between.
 */
@Component
@Slf4j
public class CacheCleaner {

    private static final int SCAN_BATCH = 100;

    @Autowired
    private RedisTemplate redisTemplate;

    /** Returns how many keys were deleted. A Redis failure is logged and reported as 0. */
    @SuppressWarnings("unchecked")
    public int deleteByPattern(String pattern) {
        try {
            List<byte[]> keys = (List<byte[]>) redisTemplate.execute(
                    (RedisCallback<List<byte[]>>) connection -> scan(connection, pattern));
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            redisTemplate.execute((RedisCallback<Long>) connection -> connection.del(keys.toArray(new byte[0][])));
            return keys.size();
        } catch (RuntimeException e) {
            // The cache only speeds reads up; a failed clean-up must not fail the menu change itself.
            log.warn("Could not clear cache entries matching {}: {}", pattern, e.getMessage());
            return 0;
        }
    }

    private static List<byte[]> scan(RedisConnection connection, String pattern) {
        List<byte[]> keys = new ArrayList<>();
        try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions().match(pattern).count(SCAN_BATCH).build())) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        }
        return keys;
    }
}
