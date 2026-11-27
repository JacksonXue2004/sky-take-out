package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.exception.ShopBusinessException;
import com.sky.mapper.ShopMapper;
import com.sky.service.ShopService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class ShopServiceImplTest {

    private static final Long MERCHANT_ID = 1L;

    @Mock
    private ShopMapper shopMapper;
    @Mock
    private RedisTemplate redisTemplate;
    @Mock
    private ValueOperations valueOperations;

    @InjectMocks
    private ShopServiceImpl shopService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        BaseContext.setCurrentId(MERCHANT_ID);
    }

    @AfterEach
    void signOut() {
        BaseContext.removeCurrentId();
    }

    // ---------- the record, for orders and the merchant ----------

    @Test
    void theStatusComesFromTheDatabase() {
        when(shopMapper.getStatus()).thenReturn(ShopService.CLOSED);

        assertEquals(ShopService.CLOSED, shopService.getStatus());
        assertFalse(shopService.isOpen());
    }

    @Test
    void aShopWithoutARowIsOpen() {
        when(shopMapper.getStatus()).thenReturn(null);

        assertEquals(ShopService.OPEN, shopService.getStatus());
        assertTrue(shopService.isOpen());
    }

    @Test
    void acceptingOrdersIgnoresTheCache() {
        lenient().when(valueOperations.get(ShopServiceImpl.CACHE_KEY)).thenReturn(ShopService.OPEN);
        when(shopMapper.getStatus()).thenReturn(ShopService.CLOSED);

        assertFalse(shopService.isOpen());
        verifyNoInteractions(redisTemplate);
    }

    // ---------- the copy, for the customer pages ----------

    @Test
    void aCachedStatusIsServedWithoutTheDatabase() {
        when(valueOperations.get(ShopServiceImpl.CACHE_KEY)).thenReturn(ShopService.CLOSED);

        assertEquals(ShopService.CLOSED, shopService.getCachedStatus());
        verifyNoInteractions(shopMapper);
    }

    @Test
    void aCacheMissReadsTheDatabaseAndCachesTheAnswer() {
        when(valueOperations.get(ShopServiceImpl.CACHE_KEY)).thenReturn(null);
        when(shopMapper.getStatus()).thenReturn(ShopService.CLOSED);

        assertEquals(ShopService.CLOSED, shopService.getCachedStatus());
        verify(valueOperations).set(ShopServiceImpl.CACHE_KEY, ShopService.CLOSED, ShopServiceImpl.CACHE_TTL);
    }

    @Test
    void anEmptiedRedisDoesNotReopenAClosedShop() {
        // What a restarted free Key Value instance looks like: the key is simply gone.
        when(valueOperations.get(ShopServiceImpl.CACHE_KEY)).thenReturn(null);
        when(shopMapper.getStatus()).thenReturn(ShopService.CLOSED);

        assertEquals(ShopService.CLOSED, shopService.getCachedStatus());
    }

    @Test
    void whenRedisIsDownTheDatabaseAnswersWithoutASecondRedisCall() {
        when(valueOperations.get(ShopServiceImpl.CACHE_KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(shopMapper.getStatus()).thenReturn(ShopService.CLOSED);

        assertEquals(ShopService.CLOSED, shopService.getCachedStatus());
        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void whenNothingCanBeReadTheShopIsReportedOpen() {
        when(valueOperations.get(ShopServiceImpl.CACHE_KEY)).thenThrow(new RedisConnectionFailureException("down"));
        when(shopMapper.getStatus()).thenThrow(new IllegalStateException("database down"));

        assertEquals(ShopService.OPEN, shopService.getCachedStatus());
    }

    // ---------- changing the status ----------

    @Test
    void savingWritesTheDatabaseBeforeTheCache() {
        shopService.setStatus(ShopService.CLOSED);

        InOrder order = inOrder(shopMapper, valueOperations);
        order.verify(shopMapper).saveStatus(eq(ShopService.CLOSED), any(LocalDateTime.class), eq(MERCHANT_ID));
        order.verify(valueOperations).set(ShopServiceImpl.CACHE_KEY, ShopService.CLOSED, ShopServiceImpl.CACHE_TTL);
    }

    @Test
    void aFailedCacheWriteStillSavesTheStatus() {
        doThrow(new RedisConnectionFailureException("down")).when(valueOperations)
                .set(ShopServiceImpl.CACHE_KEY, ShopService.OPEN, ShopServiceImpl.CACHE_TTL);

        shopService.setStatus(ShopService.OPEN);

        verify(shopMapper).saveStatus(eq(ShopService.OPEN), any(LocalDateTime.class), eq(MERCHANT_ID));
    }

    @Test
    void aDatabaseFailureIsReportedAndLeavesTheCacheAlone() {
        doThrow(new IllegalStateException("database down")).when(shopMapper)
                .saveStatus(eq(ShopService.CLOSED), any(LocalDateTime.class), eq(MERCHANT_ID));

        assertThrows(IllegalStateException.class, () -> shopService.setStatus(ShopService.CLOSED));
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void onlyOpenAndClosedAreAccepted() {
        for (Integer status : new Integer[]{2, -1, null}) {
            ShopBusinessException e = assertThrows(ShopBusinessException.class, () -> shopService.setStatus(status));
            assertEquals(MessageConstant.SHOP_STATUS_INVALID, e.getMessage());
        }
        verify(shopMapper, never()).saveStatus(any(), any(), any());
        verifyNoInteractions(redisTemplate);
    }
}
