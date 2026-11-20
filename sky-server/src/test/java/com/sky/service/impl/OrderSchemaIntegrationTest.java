package com.sky.service.impl;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.support.MySqlIntegrationTest;
import com.sky.support.TestOrders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guarantees the database enforces on its own, whatever the application code does. */
class OrderSchemaIntegrationTest extends MySqlIntegrationTest {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void twoCustomersCheckingOutAtTheSameTimeDoNotWaitForEachOther() throws Exception {
        // 20 customers with 50 cart lines each.
        List<Object[]> rows = new ArrayList<>();
        for (long user = 800_000; user < 800_020; user++) {
            for (int line = 0; line < 50; line++) {
                rows.add(new Object[]{"Burger", user, 3L, 1, "9.50"});
            }
        }
        jdbcTemplate.batchUpdate("insert into shopping_cart (name, user_id, dish_id, number, amount) values (?, ?, ?, ?, ?)", rows);
        // A freshly filled table still has the statistics of an empty one, and MySQL would plan a full
        // scan (type ALL). A real table keeps its statistics current; here they are refreshed by hand.
        jdbcTemplate.execute("analyze table shopping_cart");
        assertEquals("idx_shopping_cart_user", jdbcTemplate.queryForMap(
                "explain delete from shopping_cart where user_id = 800001").get("key"));

        try (Connection first = MYSQL.createConnection("");
             Connection second = MYSQL.createConnection("")) {
            first.setAutoCommit(false);
            second.setAutoCommit(false);
            execute(second, "set session innodb_lock_wait_timeout = 2");

            // The first checkout empties its cart and has not committed yet.
            execute(first, "delete from shopping_cart where user_id = 800001");
            long start = System.nanoTime();
            // Without idx_shopping_cart_user the first delete locked every row of the table, and this one
            // would wait two seconds and fail with "Lock wait timeout exceeded".
            execute(second, "delete from shopping_cart where user_id = 800002");
            long waitedMillis = (System.nanoTime() - start) / 1_000_000;

            assertTrue(waitedMillis < 1000, "the second checkout waited " + waitedMillis + " ms");
            first.rollback();
            second.rollback();
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Test
    void theDatabaseRejectsASecondOrderWithTheSameNumber() {
        Orders first = TestOrders.order(1L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        Orders second = TestOrders.order(2L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        second.setNumber(first.getNumber());

        orderMapper.insert(first);
        assertThrows(DuplicateKeyException.class, () -> orderMapper.insert(second));
    }

    @Test
    void aCustomerCannotStoreTheSameIdempotencyKeyTwice() {
        Orders first = TestOrders.order(3L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        first.setIdempotencyKey("key-shared-by-two-orders");
        Orders second = TestOrders.order(3L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        second.setIdempotencyKey("key-shared-by-two-orders");
        Orders otherCustomer = TestOrders.order(4L, Orders.PENDING_PAYMENT, Orders.UN_PAID);
        otherCustomer.setIdempotencyKey("key-shared-by-two-orders");

        orderMapper.insert(first);
        assertThrows(DuplicateKeyException.class, () -> orderMapper.insert(second));
        // Keys are scoped to one customer, and orders without a key never collide.
        orderMapper.insert(otherCustomer);
        orderMapper.insert(TestOrders.order(3L, Orders.PENDING_PAYMENT, Orders.UN_PAID));
        orderMapper.insert(TestOrders.order(3L, Orders.PENDING_PAYMENT, Orders.UN_PAID));
    }
}
