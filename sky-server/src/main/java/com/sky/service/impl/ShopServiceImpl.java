package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.exception.ShopBusinessException;
import com.sky.mapper.ShopMapper;
import com.sky.service.ShopService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The status used to live only in Redis under SHOP_STATUS, so a Redis restart, which empties a free
 * Render Key Value instance, quietly reopened a closed shop. The table is now the record, and this
 * class no longer reads the old key.
 */
@Service
@Slf4j
@SuppressWarnings("unchecked")
public class ShopServiceImpl implements ShopService {

    static final String CACHE_KEY = "shop_status";
    /**
     * A read that misses the cache while the status changes can store the old value after the new
     * one. The expiry bounds how long such an entry lasts.
     */
    static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Autowired
    private ShopMapper shopMapper;
    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public Integer getStatus() {
        Integer status = shopMapper.getStatus();
        return status == null ? OPEN : status;
    }

    @Override
    public boolean isOpen() {
        return OPEN.equals(getStatus());
    }

    @Override
    public Integer getCachedStatus() {
        boolean redisAnswered = false;
        try {
            Object cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached instanceof Integer) {
                return (Integer) cached;
            }
            redisAnswered = true;
        } catch (Exception e) {
            log.warn("Could not read the cached shop status, reading the database: {}", e.toString());
        }
        Integer status;
        try {
            status = getStatus();
        } catch (Exception e) {
            log.warn("Could not read the shop status, reporting the shop open: {}", e.toString());
            return OPEN;
        }
        // Writing to a Redis that just failed would only wait for a second timeout.
        if (redisAnswered) {
            cache(status);
        }
        return status;
    }

    @Override
    public void setStatus(Integer status) {
        if (!OPEN.equals(status) && !CLOSED.equals(status)) {
            throw new ShopBusinessException(MessageConstant.SHOP_STATUS_INVALID);
        }
        shopMapper.saveStatus(status, LocalDateTime.now(), BaseContext.getCurrentId());
        cache(status);
    }

    /**
     * Best effort: the database already has the status. When the write fails, an older copy can stay
     * until it expires, which changes the badge but not which orders are accepted.
     */
    private void cache(Integer status) {
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, status, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Could not cache the shop status: {}", e.toString());
        }
    }
}
