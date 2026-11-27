package com.sky.controller.admin;

import com.sky.result.Result;
import com.sky.service.ShopService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("adminShopController")
@RequestMapping("/admin/shop")
@Api(tags = "Shop API")
@Slf4j
public class ShopController {

    @Autowired
    private ShopService shopService;

    /** 1 opens the shop, 0 closes it. A failure to save is reported instead of answered as success. */
    @PutMapping("/{status}")
    @ApiOperation("Set Status")
    public Result setStatus(@PathVariable Integer status){
        shopService.setStatus(status);
        log.info("Shop status set to {}", status);
        return Result.success();
    }

    /** From the database, so the merchant sees the status that orders are checked against. */
    @GetMapping("/status")
    @ApiOperation("Get Status")
    public Result<Integer> getStatus(){
        return Result.success(shopService.getStatus());
    }
}
