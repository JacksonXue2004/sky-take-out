package com.sky.service.impl;

import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.exception.OrderBusinessException;
import com.sky.service.OrderService;
import com.sky.service.ShopService;
import com.sky.support.MySqlIntegrationTest;
import com.sky.vo.OrderSubmitVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shop's status in MySQL, through the real application. Tests run without Redis, like a free
 * Render Key Value instance that was just restarted: every read of the copy misses.
 */
class ShopStatusIntegrationTest extends MySqlIntegrationTest {

    private static final long MERCHANT_ID = 1L;

    @Autowired
    private ShopService shopService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void reopenTheShop() {
        // The other test classes share this database and place orders.
        jdbcTemplate.update("insert into shop (id, status) values (1, 1) on duplicate key update status = 1");
        BaseContext.removeCurrentId();
    }

    @Test
    void theMigrationCreatesAnOpenShop() {
        assertEquals(ShopService.OPEN, jdbcTemplate.queryForObject("select status from shop where id = 1", Integer.class));
        assertTrue(shopService.isOpen());
    }

    @Test
    void aClosedShopRefusesOrdersUntilItReopens() {
        long userId = 900_101L;
        long addressId = insertAddress(userId);
        insertCartLine(userId);

        BaseContext.setCurrentId(MERCHANT_ID);
        shopService.setStatus(ShopService.CLOSED);
        assertEquals(ShopService.CLOSED, shopService.getStatus());
        assertEquals(ShopService.CLOSED, shopService.getCachedStatus(), "without Redis the customer pages read the database");

        BaseContext.setCurrentId(userId);
        OrderBusinessException e = assertThrows(OrderBusinessException.class,
                () -> orderService.submitOrder(submission(addressId), null));
        assertEquals(MessageConstant.SHOP_CLOSED, e.getMessage());
        assertEquals(0, count("select count(*) from orders where user_id = ?", userId));
        assertEquals(1, count("select count(*) from shopping_cart where user_id = ?", userId), "the cart is kept");

        BaseContext.setCurrentId(MERCHANT_ID);
        shopService.setStatus(ShopService.OPEN);
        BaseContext.setCurrentId(userId);
        OrderSubmitVO order = orderService.submitOrder(submission(addressId), null);
        assertNotNull(order.getId());
        assertEquals(1, count("select count(*) from orders where user_id = ?", userId));
    }

    @Test
    void savingTheStatusCreatesTheRowIfItIsMissing() {
        jdbcTemplate.update("delete from shop");
        assertTrue(shopService.isOpen(), "a missing row counts as open");

        BaseContext.setCurrentId(MERCHANT_ID);
        shopService.setStatus(ShopService.CLOSED);

        assertEquals(ShopService.CLOSED, jdbcTemplate.queryForObject("select status from shop where id = 1", Integer.class));
        assertEquals(MERCHANT_ID, jdbcTemplate.queryForObject("select update_user from shop where id = 1", Long.class));
    }

    private static OrdersSubmitDTO submission(long addressId) {
        OrdersSubmitDTO dto = new OrdersSubmitDTO();
        dto.setAddressBookId(addressId);
        dto.setPayMethod(1);
        dto.setDeliveryStatus(1);
        dto.setTablewareStatus(1);
        return dto;
    }

    private long insertAddress(long userId) {
        jdbcTemplate.update("insert into address_book (user_id, consignee, phone, detail, is_default) values (?, ?, ?, ?, 1)",
                userId, "Alex", "4085550100", "1 Main St");
        return jdbcTemplate.queryForObject("select max(id) from address_book where user_id = ?", Long.class, userId);
    }

    private void insertCartLine(long userId) {
        jdbcTemplate.update("insert into shopping_cart (name, user_id, dish_id, number, amount, create_time) values (?, ?, ?, ?, ?, now())",
                "Kung Pao Chicken", userId, 101L, 1, "14.99");
    }

    private int count(String sql, Object arg) {
        return jdbcTemplate.queryForObject(sql, Integer.class, arg);
    }
}
