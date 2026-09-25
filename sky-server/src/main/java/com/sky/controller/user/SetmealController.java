package com.sky.controller.user;

import com.sky.constant.StatusConstant;
import com.sky.entity.Setmeal;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.DishItemVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController("userSetmealController")
@RequestMapping("/user/setmeal")
@Slf4j
@Api(tags = "Setmeal API")
public class SetmealController {
    @Autowired
    private SetmealService setmealService;
    @Autowired
    private RedisTemplate redisTemplate;

    @GetMapping("/list")
    @ApiOperation("List")
    public Result<List<Setmeal>> list(Long categoryId) {
        String key = categoryId != null && categoryId > 0 ? "setmealCache::" + categoryId : "setmealCache::all";
        List<Setmeal> list = null;


        try {
            list = (List<Setmeal>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Application event: {}", e.getMessage());
        }

        if(list != null && list.size() > 0){
            return Result.success(list);
        }

        Setmeal setmeal = new Setmeal();

        if(categoryId != null && categoryId > 0){
            setmeal.setCategoryId(categoryId);
        }
        setmeal.setStatus(StatusConstant.ENABLE);

        list = setmealService.list(setmeal);


        try {
            redisTemplate.opsForValue().set(key, list);
        } catch (Exception e) {
            log.warn("Application event: {}", e.getMessage());
        }

        return Result.success(list);
    }

    @GetMapping("/dish/{id}")
    @ApiOperation("Dish List")
    public Result<List<DishItemVO>> dishList(@PathVariable("id") Long id) {
        List<DishItemVO> list = setmealService.getDishItemById(id);
        return Result.success(list);
    }
}
