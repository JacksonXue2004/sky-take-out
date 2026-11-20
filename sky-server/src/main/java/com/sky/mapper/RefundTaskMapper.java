package com.sky.mapper;

import com.sky.entity.RefundTask;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Every state change below is conditional on the current status, like the order updates:
 * if two workers race for one task, only one update matches.
 */
@Mapper
public interface RefundTaskMapper {

    /** Throws DuplicateKeyException when the order already has a refund task. */
    @Insert("insert into refund_task (order_id, order_number, payment_intent_id, amount, status, attempts, " +
            "next_attempt_at, created_at, updated_at) values (#{orderId}, #{orderNumber}, #{paymentIntentId}, " +
            "#{amount}, #{status}, #{attempts}, #{nextAttemptAt}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(RefundTask task);

    @Select("select * from refund_task where status = 'PENDING' and next_attempt_at <= #{now} " +
            "order by next_attempt_at limit #{limit}")
    List<RefundTask> findDue(LocalDateTime now, int limit);

    /** Returns 1 when this worker got the task, 0 when another one claimed it first. */
    @Update("update refund_task set status = 'PROCESSING', updated_at = #{now} where id = #{id} and status = 'PENDING'")
    int claim(Long id, LocalDateTime now);

    @Update("update refund_task set status = 'SUCCEEDED', stripe_refund_id = #{stripeRefundId}, attempts = attempts + 1, " +
            "last_error = null, updated_at = #{now} where id = #{id} and status = 'PROCESSING'")
    int markSucceeded(Long id, String stripeRefundId, LocalDateTime now);

    @Update("update refund_task set status = 'PENDING', attempts = attempts + 1, next_attempt_at = #{nextAttemptAt}, " +
            "last_error = #{error}, updated_at = #{now} where id = #{id} and status = 'PROCESSING'")
    int reschedule(Long id, LocalDateTime nextAttemptAt, String error, LocalDateTime now);

    @Update("update refund_task set status = 'FAILED', attempts = attempts + 1, last_error = #{error}, " +
            "updated_at = #{now} where id = #{id} and status = 'PROCESSING'")
    int markFailed(Long id, String error, LocalDateTime now);

    /** Hands tasks back whose worker died in the middle of a Stripe call. */
    @Update("update refund_task set status = 'PENDING', updated_at = #{now} " +
            "where status = 'PROCESSING' and updated_at < #{claimedBefore}")
    int releaseStuck(LocalDateTime claimedBefore, LocalDateTime now);

    @Select("select * from refund_task where order_id = #{orderId}")
    RefundTask getByOrderId(Long orderId);
}
