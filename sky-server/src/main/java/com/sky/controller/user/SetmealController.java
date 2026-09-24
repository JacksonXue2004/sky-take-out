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
@Api(tags = "C端-套餐浏览接口")
public class SetmealController {
    @Autowired
    private SetmealService setmealService;
    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 条件查询
     *
     * @param categoryId 分类ID，为null或0时查询所有
     * @return
     */
    @GetMapping("/list")
    @ApiOperation("根据分类id查询套餐")
    public Result<List<Setmeal>> list(Long categoryId) {
        String key = categoryId != null && categoryId > 0 ? "setmealCache::" + categoryId : "setmealCache::all";
        List<Setmeal> list = null;

        //尝试从redis中获取数据
        try {
            list = (List<Setmeal>) redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis连接失败，跳过缓存查询：{}", e.getMessage());
        }

        if(list != null && list.size() > 0){
            return Result.success(list);
        }

        Setmeal setmeal = new Setmeal();
        // 只有categoryId有效时才设置分类条件
        if(categoryId != null && categoryId > 0){
            setmeal.setCategoryId(categoryId);
        }
        setmeal.setStatus(StatusConstant.ENABLE);

        list = setmealService.list(setmeal);

        //尝试将数据写入redis
        try {
            redisTemplate.opsForValue().set(key, list);
        } catch (Exception e) {
            log.warn("Redis连接失败，跳过缓存写入：{}", e.getMessage());
        }

        return Result.success(list);
    }

    /**
     * 根据套餐id查询包含的菜品列表
     *
     * @param id
     * @return
     */
    @GetMapping("/dish/{id}")
    @ApiOperation("根据套餐id查询包含的菜品列表")
    public Result<List<DishItemVO>> dishList(@PathVariable("id") Long id) {
        List<DishItemVO> list = setmealService.getDishItemById(id);
        return Result.success(list);
    }
}
