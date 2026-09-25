package com.sky.controller.admin;

import com.sky.result.Result;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "Shop API")
@Slf4j
public class ShopController {

    public static final String KEY = "SHOP_STATUS";

    @Autowired
    private RedisTemplate redisTemplate;

    @PutMapping("/{status}")
    @ApiOperation("Set Status")
    public Result setStatus(@PathVariable Integer status){
        log.info("Application event: {}",status == 1 ? "Operation" : "Operation");
        try {
            redisTemplate.opsForValue().set(KEY,status);
        } catch (Exception e) {
            log.warn("Application event: {}", e.getMessage());
        }
        return Result.success();
    }

    @GetMapping("/status")
    @ApiOperation("Get Status")
    public Result<Integer> getStatus(){
        Integer status = null;
        try {
            status = (Integer) redisTemplate.opsForValue().get(KEY);
            if (status == null) {
                status = 1;
            }
        } catch (Exception e) {
            log.warn("Application event: {}", e.getMessage());
            status = 1;
        }
        log.info("Application event: {}",status == 1 ? "Operation" : "Operation");
        return Result.success(status);
    }
}
