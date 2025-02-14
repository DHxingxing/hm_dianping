package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.Map;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ReactiveStringRedisTemplate reactiveStringRedisTemplate;

    @Override
    public Result queryByID(Long id) {

        String Key = CACHE_SHOP_KEY + id;

        String shopJson = stringRedisTemplate.opsForValue().get(Key);
        if (shopJson!=null){
            return Result.ok(JSONUtil.toBean(shopJson,Shop.class));
        }
        Shop shop = getById(id);
        if (shop == null){
            return Result.fail("店铺不存在");
        }
        stringRedisTemplate.opsForValue().set(Key,JSONUtil.toJsonStr(shop));
        return Result.ok(shop);

    }
}
