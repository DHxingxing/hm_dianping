package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;

import static com.hmdp.utils.RedisConstant.CACHE_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IShopTypeService typeService;

    @Override
    public Result queryList() {


    String JsonType = stringRedisTemplate.opsForValue().get(CACHE_TYPE_KEY);
    if(JsonType != null){
        return Result.ok(JSONUtil.toList(JsonType,ShopType.class));
    }
    List<ShopType> typeList = typeService.query().orderByAsc("sort").list();

    if(typeList == null){
        return Result.fail("列表不存在了");
    }
    //java 中任何对象都可以转为json对象
    stringRedisTemplate.opsForValue().set(CACHE_TYPE_KEY,JSONUtil.toJsonStr(typeList));

    return Result.ok(typeList);
    }
}
