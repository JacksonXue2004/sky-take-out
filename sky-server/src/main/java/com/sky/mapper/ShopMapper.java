package com.sky.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/** The shop's one row in the shop table (id 1), created by 005-shop-status.sql. */
@Mapper
public interface ShopMapper {

    /** 1 open, 0 closed, or null when the row does not exist. */
    @Select("select status from shop where id = 1")
    Integer getStatus();

    /** Creates the row if it is missing, so the status can always be saved. */
    @Insert("insert into shop (id, status, update_time, update_user) values (1, #{status}, #{updateTime}, #{updateUser}) " +
            "on duplicate key update status = #{status}, update_time = #{updateTime}, update_user = #{updateUser}")
    void saveStatus(@Param("status") Integer status, @Param("updateTime") LocalDateTime updateTime,
                    @Param("updateUser") Long updateUser);
}
