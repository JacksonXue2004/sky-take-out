package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Dish;
import com.sky.result.Result;
import com.sky.service.DishService;
import com.sky.vo.DishVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userDishController")
@RequestMapping("/user/dish")
@Slf4j
@Api(tags = "Dish API")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("/list")
    @ApiOperation("List")
    public Result<List<DishVO>> list(Long categoryId) {

        List<DishVO> list = null;
        String key = categoryId != null && categoryId > 0 ? "dish_" + categoryId : "dish_all";


        try {
            list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Application event: {}", e.getMessage());
        }

        if(list != null && list.size() > 0){
            return Result.success(list);
        }

        Dish dish = new Dish();

        if(categoryId != null && categoryId > 0){
            dish.setCategoryId(categoryId);
        }
        dish.setStatus(StatusConstant.ENABLE);


        list = dishService.listWithFlavor(dish);


        try {
            redisTemplate.opsForValue().set(key, list);
        } catch (Exception e) {
            log.warn("Application event: {}", e.getMessage());
        }

        return Result.success(list);
    }

}
