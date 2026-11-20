package com.sky.mapper;

import com.sky.entity.WebhookEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface WebhookEventMapper {

    /** Throws DuplicateKeyException when an event with the same event_id was stored before. */
    @Insert("insert into webhook_event (event_id, type, status, order_number, error, payload, attempts, received_at, processed_at) " +
            "values (#{eventId}, #{type}, #{status}, #{orderNumber}, #{error}, #{payload}, #{attempts}, #{receivedAt}, #{processedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(WebhookEvent event);

    @Select("select * from webhook_event where event_id = #{eventId}")
    WebhookEvent getByEventId(String eventId);

    @Update("update webhook_event set status = #{status}, order_number = #{orderNumber}, error = #{error}, " +
            "processed_at = #{processedAt} where id = #{id}")
    void finish(WebhookEvent event);

    @Update("update webhook_event set attempts = attempts + 1 where id = #{id}")
    void countAttempt(Long id);
}
