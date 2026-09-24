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
@Api(tags = "C端-菜品浏览接口")
public class DishController {
    @Autowired
    private DishService dishService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 根据分类id查询菜品
     *
     * @param categoryId 分类ID，为null或0时查询所有
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询菜品")
    public Result<List<DishVO>> list(Long categoryId) {

        List<DishVO> list = null;
        String key = categoryId != null && categoryId > 0 ? "dish_" + categoryId : "dish_all";

        //尝试从redis中获取数据，Redis不可用时回退到数据库
        try {
            list = (List<DishVO>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis连接失败，跳过缓存查询：{}", e.getMessage());
        }

        if(list != null && list.size() > 0){
            return Result.success(list);
        }

        Dish dish = new Dish();
        // 只有categoryId有效时才设置分类条件
        if(categoryId != null && categoryId > 0){
            dish.setCategoryId(categoryId);
        }
        dish.setStatus(StatusConstant.ENABLE);

        //查询数据库
        list = dishService.listWithFlavor(dish);

        //尝试将数据写入redis，Redis不可用时忽略异常
        try {
            redisTemplate.opsForValue().set(key, list);
        } catch (Exception e) {
            log.warn("Redis连接失败，跳过缓存写入：{}", e.getMessage());
        }

        return Result.success(list);
    }

}
