package com.sky.controller.user;

import com.sky.result.Result;
import com.sky.service.ShopService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userShopController")
@RequestMapping("/user/shop")
@Api(tags = "Shop API")
public class ShopController {

    @Autowired
    private ShopService shopService;

    /**
     * Read from the Redis copy and never fails, because every page asks for it and Render's health
     * check uses it. Placing an order checks the database instead.
     */
    @GetMapping("/status")
    @ApiOperation("Get Status")
    public Result<Integer> getStatus(){
        return Result.success(shopService.getCachedStatus());
    }
}
